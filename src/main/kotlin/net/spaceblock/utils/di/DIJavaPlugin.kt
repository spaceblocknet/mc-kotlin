package net.spaceblock.utils.di

import net.spaceblock.utils.adventure.text
import net.spaceblock.utils.di.commands.Command
import net.spaceblock.utils.di.commands.CommandsHelper
import net.spaceblock.utils.di.commands.TabComplete
import net.spaceblock.utils.di.events.Event
import net.spaceblock.utils.di.events.EventHelper
import net.spaceblock.utils.di.serverevents.OnDisable
import net.spaceblock.utils.di.serverevents.OnEnable
import net.spaceblock.utils.di.serverevents.OnLoad
import net.spaceblock.utils.di.serverevents.ServerEventsHelper
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.functions

abstract class DIJavaPlugin : JavaPlugin() {

    private val stereotypes = arrayOf(MinecraftController::class, Service::class, Repository::class)

    abstract fun initDI()
    abstract fun startDI()
    abstract fun stopDI()

    abstract fun scanForMinecraftStereotypes(annotation: Array<KClass<out Annotation>>, packagePath: String = projectPackagePath): List<KClass<*>>
    abstract fun <T : Any> getDI(type: KClass<T>, qualifier: String? = null): T?
    abstract fun getQualifier(annotation: List<Annotation>): String?

    abstract val projectPackagePath: String

    protected lateinit var stereotypesClasses: List<KClass<*>>

    override fun onLoad() {
        initDI()
        logger.info("Scanning for Minecraft controllers in $projectPackagePath")
        stereotypesClasses = scanForMinecraftStereotypes(stereotypes, projectPackagePath)
        logger.info("Found ${stereotypesClasses.size} Minecraft Stereotypes in $projectPackagePath")
        startDI()
        scanForMinecraftAnnotationsInClassesOnLoad(stereotypesClasses)

        ServerEventsHelper.triggerOnLoad(this)
    }

    final override fun onEnable() {
        ServerEventsHelper.triggerOnEnable(this)
        scanForMinecraftAnnotationsInClassesOnEnable(stereotypesClasses)
    }

    final override fun onDisable() {
        ServerEventsHelper.triggerOnDisable(this)

        stopDI()
    }

    fun getParameterMap(parameters: List<KParameter>, vararg additional: Any?): Map<KParameter, Any?> = parameters.associateWith { parameter ->
        val qualifier = getQualifier(parameter.annotations)

        val type = if (parameter.type.classifier is KClass<*>) {
            parameter.type.classifier as KClass<*>
        } else {
            error("Unexpected classifier type: ${parameter.type.classifier}")
        }

        val additionalValue = additional.filterNotNull().firstOrNull { type.isInstance(it) }
        val value = additionalValue ?: getDI(type, qualifier)

        if (value == null && !parameter.isOptional) {
            error("Could not find a value for parameter ${parameter.name} of type ${parameter.type} with qualifier $qualifier")
        }

        value
    }

    private fun scanForMinecraftAnnotationsInClassesOnEnable(classes: List<KClass<*>>) {
        classes
            .filter { it.findAnnotations(MinecraftController::class).isNotEmpty() }
            .forEach { clazz ->
                clazz.functions.forEach { func ->
                    func.annotations.forEach { annotation ->
                        when (annotation) {
                            is Command -> {
                                CommandsHelper.registerCommand(this, annotation, func)
                            }

                            is TabComplete -> {
                                CommandsHelper.registerTabComplete(this, annotation, func)
                            }

                            is Event -> {
                                EventHelper.registerEvent(this, annotation, func)
                            }
                        }
                    }
                }
            }
    }

    private fun scanForMinecraftAnnotationsInClassesOnLoad(classes: List<KClass<*>>) {
        classes
            .filter { it.findAnnotations(MinecraftController::class).isNotEmpty() }
            .forEach { clazz ->
                clazz.functions.forEach { func ->
                    func.annotations.forEach { annotation ->
                        when (annotation) {
                            is OnEnable -> {
                                ServerEventsHelper.registerOnEnable(func)
                            }

                            is OnDisable -> {
                                ServerEventsHelper.registerOnDisable(func)
                            }

                            is OnLoad -> {
                                ServerEventsHelper.registerOnLoad(func)
                            }
                        }
                    }
                }
            }
    }

    // Lock this stuff down
    final override fun onTabComplete(sender: CommandSender, command: org.bukkit.command.Command, alias: String, args: Array<out String>?): MutableList<String> {
        sender.sendMessage(text("An error occurred while executing this command. Please contact the server administrators."))
        logger.warning("OnTabComplete was called for ${command.name} with alias $alias and args ${args?.joinToString(", ")} but no tab complete was registered.")
        return emptyList<String>().toMutableList()
    }

    final override fun onCommand(sender: CommandSender, command: org.bukkit.command.Command, label: String, args: Array<out String>?): Boolean {
        sender.sendMessage(text("An error occurred while executing this command. Please contact the server administrators."))
        logger.warning("OnCommand was called for ${command.name} with alias $label and args ${args?.joinToString(", ")} but no command was registered.")
        return false
    }
}
