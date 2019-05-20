package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provider


class CourseAppModule : KotlinModule() {

    override fun configure() {
        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()

        class KVStoreProvider : Provider<KeyValueStore> {
            override fun get(): KeyValueStore {
                return KeyValueStoreImpl(CourseAppImplInitializer.storage)
            }
        }

        bind<KeyValueStore>().toProvider(KVStoreProvider())
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
    }
}