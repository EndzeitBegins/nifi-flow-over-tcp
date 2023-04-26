package io.github.endzeitbegins.nifi.flowovertcp.utils

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.deleteExisting

internal fun Path.deleteRegularFilesRecursively() {
    Files.walkFileTree(this, object : FileVisitor<Path> {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (!Files.isHidden(file)) {
                file.deleteExisting()
            }

            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
            if (exc != null) {
                throw exc
            }

            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
            if (exc != null) {
                throw exc
            }

            return FileVisitResult.CONTINUE
        }

    })
}