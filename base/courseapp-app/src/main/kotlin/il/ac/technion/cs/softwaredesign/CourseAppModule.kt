package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provider
import il.ac.technion.cs.softwaredesign.storage.SecureStorage

class SecureStorageProvider : Provider<SecureStorage> {
    override fun get(): SecureStorage {
        return CourseAppImplInitializer.storage
    }
}

class CourseAppModule : KotlinModule() {

    override fun configure() {
        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<SecureStorage>().toProvider(SecureStorageProvider())
        bind<KeyValueStore>().to<KeyValueStoreImpl>()
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()
    }
}