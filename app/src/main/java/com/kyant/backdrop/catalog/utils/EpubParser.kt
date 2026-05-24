package com.kyant.backdrop.catalog.utils

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

data class ParsedBook(
    val id: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val pages: List<ParsedPage>
)

data class ParsedPage(
    val chapterTitle: String,
    val content: String
)

object EpubParser {
    fun parsePdf(context: Context, pdfFile: File): ParsedBook {
        PDFBoxResourceLoader.init(context)
        val bookId = UUID.randomUUID().toString()
        val destDir = File(context.filesDir, "books/$bookId")
        destDir.mkdirs()

        var title = "Unknown Document"
        var author = "Unknown Author"
        val pages = mutableListOf<ParsedPage>()

        try {
            PDDocument.load(pdfFile).use { document ->
                val info = document.documentInformation
                if (info != null) {
                    if (!info.title.isNullOrBlank()) title = info.title
                    if (!info.author.isNullOrBlank()) author = info.author
                }

                val stripper = PDFTextStripper()
                val totalPages = document.numberOfPages
                for (i in 1..totalPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(document)
                    if (text.trim().isNotEmpty()) {
                        val chunks = splitIntoPages(text, 6500)
                        for (chunk in chunks) {
                            pages.add(ParsedPage("Page $i", chunk))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EpubParser", "Failed to parse PDF", e)
            throw RuntimeException("Failed to scan PDF", e)
        }

        return ParsedBook(bookId, title, author, null, pages)
    }

    fun parseEpub(context: Context, epubFile: File): ParsedBook {
        val bookId = UUID.randomUUID().toString()
        val destDir = File(context.filesDir, "books/$bookId")
        destDir.mkdirs()

        var title = epubFile.nameWithoutExtension.replace("_", " ")
        var author = "Unknown Author"
        var coverPath: String? = null
        val pages = mutableListOf<ParsedPage>()

        try {
            val zip = ZipFile(epubFile)
            val containerEntry = zip.getEntry("META-INF/container.xml")
            var opfPath = ""
            if (containerEntry != null) {
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                val opfMatch = "full-path=\"([^\"]+)\"".toRegex().find(containerXml)
                if (opfMatch != null) {
                    opfPath = opfMatch.groupValues[1]
                }
            }

            if (opfPath.isEmpty()) {
                val opfEntry = zip.entries().asSequence().find { it.name.endsWith(".opf") }
                if (opfEntry != null) {
                    opfPath = opfEntry.name
                }
            }

            if (opfPath.isNotEmpty()) {
                val opfEntry = zip.getEntry(opfPath)
                if (opfEntry != null) {
                    val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                    
                    val titleMatch = "<dc:title[^>]*>([^<]+)</dc:title>".toRegex(RegexOption.IGNORE_CASE).find(opfXml)
                    if (titleMatch != null) {
                        title = titleMatch.groupValues[1].trim()
                    }

                    val creatorMatch = "<dc:creator[^>]*>([^<]+)</dc:creator>".toRegex(RegexOption.IGNORE_CASE).find(opfXml)
                    if (creatorMatch != null) {
                        author = creatorMatch.groupValues[1].trim()
                    }

                    val opfDirectory = File(opfPath).parent?.plus("/") ?: ""
                    var coverId = ""
                    val metaCoverMatch = "<meta[^>]+name=\"cover\"[^>]+content=\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).find(opfXml)
                        ?: "<meta[^>]+content=\"([^\"]+)\"[^>]+name=\"cover\"".toRegex(RegexOption.IGNORE_CASE).find(opfXml)
                    if (metaCoverMatch != null) {
                        coverId = metaCoverMatch.groupValues[1]
                    }

                    var coverHref = ""
                    val items = mutableMapOf<String, String>()
                    val itemMediaTypes = mutableMapOf<String, String>()
                    
                    val itemRegex = "<item\\s+([^>]+)>".toRegex(RegexOption.IGNORE_CASE)
                    for (m in itemRegex.findAll(opfXml)) {
                        val attrs = m.groupValues[1]
                        val id = "id=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1) ?: ""
                        val href = "href=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1) ?: ""
                        val mediaType = "media-type=\"([^\"]+)\"".toRegex().find(attrs)?.groupValues?.get(1) ?: ""
                        if (id.isNotEmpty() && href.isNotEmpty()) {
                            items[id] = href
                            itemMediaTypes[id] = mediaType
                            if (id.equals(coverId, ignoreCase = true) || id.contains("cover", ignoreCase = true)) {
                                if (mediaType.startsWith("image/", ignoreCase = true)) {
                                    coverHref = href
                                }
                            }
                        }
                    }

                    if (coverHref.isEmpty()) {
                        val key = items.keys.find { it.contains("cover", ignoreCase = true) }
                            ?: items.entries.find { it.value.contains("cover", ignoreCase = true) }?.key
                        if (key != null) {
                            coverHref = items[key] ?: ""
                        }
                    }

                    if (coverHref.isEmpty()) {
                        val firstImage = items.entries.find { itemMediaTypes[it.key]?.startsWith("image/", ignoreCase = true) == true }
                        if (firstImage != null) {
                            coverHref = firstImage.value
                        }
                    }

                    if (coverHref.isNotEmpty()) {
                        val decodedCoverHref = java.net.URLDecoder.decode(coverHref, "UTF-8")
                        val zipCoverPath = resolveRelativePath(opfDirectory, decodedCoverHref)
                        // Search for entries matching ignoring base directory prefix if full match fails
                        val coverEntry = zip.getEntry(zipCoverPath) 
                            ?: zip.entries().asSequence().find { it.name.endsWith(File(zipCoverPath).name) }
                        if (coverEntry != null) {
                            val coversDir = File(context.filesDir, "covers")
                            coversDir.mkdirs()
                            val extractedCoverFile = File(coversDir, "${bookId}_cover.jpg")
                            zip.getInputStream(coverEntry).use { input ->
                                extractedCoverFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            coverPath = extractedCoverFile.absolutePath
                        }
                    }
                    
                    // Extract all images
                    val imageItems = items.filter { itemMediaTypes[it.key]?.startsWith("image/", ignoreCase = true) == true }
                    val imagePathMap = mutableMapOf<String, String>()
                    for ((_, imgHref) in imageItems) {
                        val decodedImgHref = java.net.URLDecoder.decode(imgHref, "UTF-8")
                        val zipImgPath = resolveRelativePath(opfDirectory, decodedImgHref)
                        val imgEntry = zip.getEntry(zipImgPath)
                            ?: zip.entries().asSequence().find { it.name.endsWith(File(zipImgPath).name) }
                        if (imgEntry != null) {
                            val extractedImgFile = File(destDir, File(zipImgPath).name.replace(" ", "_"))
                            zip.getInputStream(imgEntry).use { input ->
                                extractedImgFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            imagePathMap[File(zipImgPath).name] = extractedImgFile.absolutePath
                        }
                    }

                    val spineMatches = "<itemref\\s+idref=\"([^\"]+)\"".toRegex(RegexOption.IGNORE_CASE).findAll(opfXml)
                    val readingOrderIds = spineMatches.map { it.groupValues[1] }.toList()

                    for (id in readingOrderIds) {
                        val href = items[id] ?: continue
                        val decodedHref = java.net.URLDecoder.decode(href, "UTF-8")
                        val zipChapterPath = resolveRelativePath(opfDirectory, decodedHref)
                        val chapterEntry = zip.getEntry(zipChapterPath)
                            ?: zip.entries().asSequence().find { it.name.endsWith(File(zipChapterPath).name) }
                        if (chapterEntry != null) {
                            val htmlContent = zip.getInputStream(chapterEntry).bufferedReader().use { it.readText() }
                            
                            var chapterTitle = "Chapter ${pages.size + 1}"
                            val chTitleMatch = "<title>([^<]+)</title>".toRegex(RegexOption.IGNORE_CASE).find(htmlContent)
                            if (chTitleMatch != null && chTitleMatch.groupValues[1].trim().isNotEmpty()) {
                                chapterTitle = chTitleMatch.groupValues[1].trim()
                            }

                            val plainText = processHtmlWithImages(htmlContent, imagePathMap)
                            if (plainText.trim().isNotEmpty()) {
                                val chunks = splitIntoPages(plainText, 6500)
                                for (chunk in chunks) {
                                    pages.add(ParsedPage(chapterTitle, chunk))
                                }
                            }
                        }
                    }
                }
            }

            if (pages.isEmpty()) {
                val htmlFiles = zip.entries().asSequence()
                    .filter { it.name.endsWith(".html") || it.name.endsWith(".xhtml") }
                    .sortedBy { it.name }
                    .toList()

                // If pages are empty but we have images parsed implicitly from fallback html, we can't easily map them without OPF but we can try!
                // To keep it simple, we just pass empty map or we could parse images here too if we want, but it's an edge case.
                for (entry in htmlFiles) {
                    val htmlContent = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    var chapterTitle = "Chapter ${pages.size + 1}"
                    val chTitleMatch = "<title>([^<]+)</title>".toRegex(RegexOption.IGNORE_CASE).find(htmlContent)
                    if (chTitleMatch != null) {
                        chapterTitle = chTitleMatch.groupValues[1].trim()
                    }
                    val plainText = stripHtml(htmlContent)
                    if (plainText.trim().isNotEmpty()) {
                        val chunks = splitIntoPages(plainText, 6500)
                        for (chunk in chunks) {
                            pages.add(ParsedPage(chapterTitle, chunk))
                        }
                    }
                }
            }

            zip.close()
        } catch (e: Exception) {
            Log.e("EpubParser", "Error parsing epub", e)
        }

        if (pages.isEmpty()) {
            pages.add(ParsedPage("Introduction", "This book was uploaded successfully, but could not be parsed sequentially. Enjoy reading!"))
        }

        return ParsedBook(bookId, title, author, coverPath, pages)
    }

    private fun processHtmlWithImages(html: String, imageMap: Map<String, String>): String {
        val imgRegex = "<(?:img|image)[^>]+(?:src|href)=['\"]([^'\"]+)['\"][^>]*>".toRegex(RegexOption.IGNORE_CASE)
        val textWithImages = imgRegex.replace(html) { matchResult ->
            val src = matchResult.groupValues[1]
            val fileName = File(src.substringBefore("?").substringBefore("#")).name
            
            // Look for URL decoded match if direct match fails
            val decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8")
            val imgPath = imageMap[fileName] ?: imageMap[decodedFileName] ?: imageMap.entries.find { it.key.equals(fileName, true) }?.value
            
            if (imgPath != null) {
                "\n\n[IMG:$imgPath]\n\n"
            } else {
                "" // Strip images if they aren't mapped
            }
        }
        return stripHtml(textWithImages)
    }

    private fun resolveRelativePath(baseDir: String, relativePath: String): String {
        if (baseDir.isEmpty()) return relativePath
        val normalizedBase = baseDir.replace("\\", "/")
        val normalizedRelative = relativePath.replace("\\", "/")
        
        // Simple directory traversal logic
        val parts = (normalizedBase + normalizedRelative).split("/")
        val result = mutableListOf<String>()
        for (part in parts) {
            if (part == "." || part.isEmpty()) continue
            if (part == "..") {
                if (result.isNotEmpty()) result.removeAt(result.size - 1)
            } else {
                result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun stripHtml(html: String): String {
        var text = html
            .replace("(?i)<script[\\s\\S]*?>[\\s\\S]*?</script>".toRegex(), "")
            .replace("(?i)<style[\\s\\S]*?>[\\s\\S]*?</style>".toRegex(), "")
            .replace("(?i)<head[\\s\\S]*?>[\\s\\S]*?</head>".toRegex(), "")
            .replace("(?i)</?p[^>]*>".toRegex(), "\n\n")
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</?div[^>]*>".toRegex(), "\n\n")
            .replace("(?i)</?h[1-6][^>]*>".toRegex(), "\n\n")
            .replace("<[^>]+>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("(?m)^[ \\t]+".toRegex(), "") // remove leading spaces on lines
            .replace("\n{3,}".toRegex(), "\n\n") // Squash more than 2 newlines
            .trim()
        return text
    }

    private fun splitIntoPages(text: String, pageSize: Int): List<String> {
        val pages = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val end = minOf(start + pageSize, text.length)
            var adjustedEnd = end
            if (end < text.length) {
                val lastSpace = text.substring(start, end).lastIndexOf(' ')
                if (lastSpace > pageSize / 2) {
                    adjustedEnd = start + lastSpace + 1
                }
            }
            pages.add(text.substring(start, adjustedEnd).trim())
            start = adjustedEnd
        }
        return pages
    }
}
