package co.nilin.opex.storage.app

import co.nilin.opex.utility.error.EnableOpexErrorHandler
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling
import springfox.documentation.swagger2.annotations.EnableSwagger2

@SpringBootApplication
@ComponentScan("co.nilin.opex")
@EnableOpexErrorHandler
@EnableScheduling
class StorageApp

fun main(args: Array<String>) {
    runApplication<StorageApp>(*args)
}
