package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule

open class CourseAppModule : KotlinModule() {
    override fun configure() {
        bind<CourseApp>().to<CourseAppImpl>()
    }
}