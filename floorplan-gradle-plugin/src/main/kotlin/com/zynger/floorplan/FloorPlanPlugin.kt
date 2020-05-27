package com.zynger.floorplan

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.TestedExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import java.io.File

/**
 * Usage of this plugin
 *
 *
 * """
 * apply plugin: FloorPlanPlugin
 *
 * floorPlan {
 *  outputDir = "path for output"
 *  creationSqlAsTableNote = true // optional, defaults to false
 *  renderNullableFields = true // optional, defaults to false
 * }
 *
 * """
 */
class FloorPlanPlugin : Plugin<Project> {

    companion object {
        private const val ARG_ROOM_SCHEMA_LOCATION = "room.schemaLocation"
    }

    override fun apply(project: Project) {
        if (project.isAndroidLibraryOrAppProject()) {
            val floorPlanExtension = project.extensions.create("floorPlan", FloorPlanExtension::class.java)

            project.afterEvaluate {
                val roomSchemaDirs = findRoomSchemaDirs(project)

                project.tasks
                    .register("generateFloorPlan", FloorPlanTask::class.java)
                    .configure { task ->
                        task.roomSchemaDirectories = roomSchemaDirs.toList()
                        task.creationSqlAsTableNote = floorPlanExtension.creationSqlAsTableNote
                        task.renderNullableFields = floorPlanExtension.renderNullableFields
                        task.floorPlanOutputDir = File(floorPlanExtension.outputDir)
                    }
            }
        }
    }

    private fun findRoomSchemaDirs(
        project: Project
    ): Set<File> {
        val roomSchemaDirs = mutableSetOf<File>()

        // attempt to get arguments from android block
        val androidExtension = project.extensions.findByType(TestedExtension::class.java)
        androidExtension?.forEachVariant { variant ->
            variant.getAnnotationProcessorArgument(ARG_ROOM_SCHEMA_LOCATION)
                ?.let {
                    roomSchemaDirs.add(File(it))
                }
        }

        // attempt to get arguments from kapt
        val kaptExtension = project.extensions.findByType(KaptExtension::class.java)
        kaptExtension
            ?.getAdditionalArguments(project, null, androidExtension)
            ?.get(ARG_ROOM_SCHEMA_LOCATION)
            ?.let {
                roomSchemaDirs.add(File(it))
            }

        return roomSchemaDirs
    }

    private fun BaseVariant.getAnnotationProcessorArgument(arg: String): String? {
        return this.javaCompileOptions.annotationProcessorOptions.arguments[arg]
    }

    private fun TestedExtension.forEachVariant(action: (BaseVariant) -> Unit) {
        when (this) {
            is AppExtension -> {
                applicationVariants.all { variant ->
                    action(variant)
                }
            }
            is LibraryExtension -> {
                libraryVariants.all { variant ->
                    action(variant)
                }
            }
            else -> throw IllegalArgumentException(
                "Error applying this on variants. " +
                        "Please, ensure that you have applied this plugin on either an Android library or application module"
            )
        }
    }

    private fun Project.isAndroidLibraryOrAppProject(): Boolean {
        return this.plugins.hasPlugin(LibraryPlugin::class.java) ||
                this.plugins.hasPlugin(AppPlugin::class.java)
    }
}
