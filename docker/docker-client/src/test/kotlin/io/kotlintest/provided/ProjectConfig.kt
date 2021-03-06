package io.kotlintest.provided

import io.kotlintest.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    init {
        if(System.getProperty("java.io.tmpdir") == """C:\windows\""")
            System.setProperty("java.io.tmpdir", """build\tmp""" )
    }
}