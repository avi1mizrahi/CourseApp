package il.ac.technion.cs.softwaredesign.dataTypeProxies

import com.google.inject.Inject


class Managers @Inject constructor() {
    @Inject
    lateinit var users : UserManager
    @Inject
    lateinit var tokens : TokenManager
    @Inject
    lateinit var channels : ChannelManager
    @Inject
    lateinit var messages : MessageManager
}