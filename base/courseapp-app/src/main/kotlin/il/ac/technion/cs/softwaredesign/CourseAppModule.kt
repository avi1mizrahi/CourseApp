package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorage


class CourseAppModule : KotlinModule() {

    override fun configure() {
        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<SecureStorage>().toInstance(CourseAppImplInitializer.storage)
        bind<KeyValueStore>().to<KeyValueStoreImpl>()
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
    }
}