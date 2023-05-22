package net.spaceblock.utils.di

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Key
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.google.inject.name.Names
import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotations

abstract class GuiceJavaPlugin : DIJavaPlugin() {

    private lateinit var injector: Injector

    override fun startDI() {
        logger.info("Starting Guice injector")
        val module = MinecraftGuiceModule(this, stereotypesClasses, classLoader)

        injector = Guice.createInjector(module) ?: error("Could not create Guice injector")

        stereotypesClasses
            .filter { it.findAnnotations(MinecraftController::class).isNotEmpty() }
            .forEach {
                injector.getInstance(it.java)
            }
    }

    override fun <T : Any> getDI(type: KClass<T>, qualifier: String?): T? {
        return if (qualifier == null) {
            injector.getInstance(type.java)
        } else {
            val key: Key<T> = Key.get(type.java, Names.named(qualifier))

            injector.getInstance(key) ?: injector.getInstance(type.java)
        }
    }

    override fun getQualifier(annotation: List<Annotation>): String? {
        return annotation
            .filter { it.annotationClass == Named::class }
            .map { it as Named }
            .firstOrNull()
            ?.value
    }
}

class MinecraftGuiceModule(
    private val plugin: GuiceJavaPlugin,
    private val allStereotypes: List<KClass<*>>,
    private val classLoader: ClassLoader
) : AbstractModule() {
    override fun configure() {
        bind(JavaPlugin::class.java).toInstance(plugin)
        bind(GuiceJavaPlugin::class.java).toInstance(plugin)
        bind(DIJavaPlugin::class.java).toInstance(plugin)
        bind(Server::class.java).toInstance(plugin.server)
        bind(ClassLoader::class.java).toInstance(classLoader)

        bind(Logger::class.java).annotatedWith(Names.named("pluginLogger")).toInstance(plugin.logger)

        allStereotypes.forEach {
            bind(it.java).`in`(Singleton::class.java)
        }
    }
}
