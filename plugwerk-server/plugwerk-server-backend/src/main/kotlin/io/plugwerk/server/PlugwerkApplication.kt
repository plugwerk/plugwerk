package io.plugwerk.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PlugwerkApplication

fun main(args: Array<String>) {
    runApplication<PlugwerkApplication>(*args)
}
