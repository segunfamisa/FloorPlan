package com.zynger.floorplan

import com.zynger.floorplan.room.RoomConsumer
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class FloorPlanTask : DefaultTask() {

    data class FloorPlanInput(
        val schemaPath: String,
        val outputPath: String?,
        val creationSqlAsTableNote: Boolean,
        val renderNullableFields: Boolean
    )

    @get:InputFiles
    lateinit var roomSchemaDirectories: List<File>

    @get:Input
    var creationSqlAsTableNote: Boolean = false

    @get:Input
    var renderNullableFields: Boolean = false

    @OutputDirectory
    lateinit var floorPlanOutputDir: File

    @TaskAction
    fun generateFloorPlan() {
        val schemas = findSchemas(roomSchemaDirectories)

        schemas.forEach { file ->
            val input = FloorPlanInput(
                schemaPath = file.absolutePath,
                outputPath = file.createOutputPath(),
                creationSqlAsTableNote = creationSqlAsTableNote,
                renderNullableFields = renderNullableFields
            )

            val src = File(input.schemaPath)
            val project = RoomConsumer.read(src)

            // render floor plan in dbml output
            FloorPlan.render(
                project = project,
                output = Output(
                    Format.DBML(
                        DbmlConfiguration(
                            input.creationSqlAsTableNote,
                            input.renderNullableFields
                        )
                    ),
                    if (input.outputPath == null) Destination.StandardOut else Destination.Disk(File(input.outputPath))
                )
            )
        }
    }

    /**
     * Creates an output file in the format: <schema-parent-directory-name>_<schema-filename>.dbml
     */
    private fun File.createOutputPath(): String {
        val parent = this.parentFile
            ?: throw IllegalArgumentException("Please ensure that the schema file (${this.absolutePath}) is in a directory")

        val outFileName = "${parent.name}_${this.nameWithoutExtension}.dbml"
        return "${floorPlanOutputDir.absolutePath}${File.separator}$outFileName"
    }

    private fun findSchemas(
        roomSchemaDirs: List<File>
    ): List<File> {
        return roomSchemaDirs.map { schemaDir ->
            schemaDir.subDirectories()
                .map { dbSchemaDir ->
                    findLatestSchemaVersion(dbSchemaDir)
                }
        }.flatten()
    }

    private fun findLatestSchemaVersion(dbDir: File): File {
        val files = dbDir.listJsonFiles()
        return files.maxBy { schema ->
            schema.nameWithoutExtension.toInt()
        } ?: throw IllegalArgumentException("No schemas in this directory: ${dbDir.absolutePath}")
    }

    private fun File.subDirectories(): List<File> {
        return this.listFiles { dir ->
            dir.isDirectory
        }?.toList() ?: listOf()
    }

    private fun File.listJsonFiles(): List<File> {
        return this.listFiles { dir ->
            dir.extension == "json"
        }?.toList() ?: listOf()
    }
}
