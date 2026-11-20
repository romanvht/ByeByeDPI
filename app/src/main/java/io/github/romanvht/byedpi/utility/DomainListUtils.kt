package io.github.romanvht.byedpi.utility

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.data.DomainList
import java.io.File
import java.io.FileNotFoundException

object DomainListUtils {
    private const val TAG = "DomainListUtils"
    private const val DOMAIN_LISTS_FILE = "domain_lists.json"

    private val gson = Gson()

    /** The lists file is used by the screens and by the strategy test at the same time. */
    private val lock = Any()

    /**
     * Display names for built-in lists whose file name is not a good name. Only the letter case may
     * differ from the id, because commands refer to lists as {list:name}, ignoring case.
     */
    private val builtInNames = mapOf("youtube" to "YouTube", "googlevideo" to "GoogleVideo")

    private fun getDefaultActiveIds(lang: String): Set<String> = when (lang) {
        "tr" -> setOf("türkiye", "discord")
        else -> setOf("youtube", "googlevideo")
    }

    private fun listsFile(context: Context) = AtomicFile(File(context.filesDir, DOMAIN_LISTS_FILE))

    /** Adds new built-in lists and updates the ones the user did not change. */
    fun syncLists(context: Context) {
        synchronized(lock) {
            val currentLists = getAllLists(context)

            val builtInMap = currentLists
                .filter { it.isBuiltIn }
                .associateBy { it.id }

            val assetFiles = context.assets.list("")?.filter {
                it.startsWith("proxytest_") && it.endsWith(".sites")
            } ?: emptyList()

            val newBuiltInIds = mutableSetOf<String>()

            for (assetFile in assetFiles) {
                val id = assetFile
                    .removePrefix("proxytest_")
                    .removeSuffix(".sites")

                newBuiltInIds.add(id)

                val domains = try {
                    context.assets.open(assetFile)
                        .bufferedReader()
                        .useLines { it.toList() }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } catch (e: Exception) {
                    // One broken list should not break the others
                    Log.e(TAG, "Could not read built-in list $assetFile", e)
                    continue
                }

                val existing = builtInMap[id]

                when {
                    existing == null -> {
                        currentLists.add(
                            DomainList(
                                id = id,
                                name = builtInNames[id] ?: id.replaceFirstChar { it.uppercase() },
                                domains = domains,
                                isActive = id in getDefaultActiveIds(SettingsUtils.getCurrentLanguage(context)),
                                isBuiltIn = true
                            )
                        )
                    }

                    existing.isDeleted || existing.isModified -> continue

                    else -> {
                        val index = currentLists.indexOfFirst { it.id == id }
                        currentLists[index] = existing.copy(domains = domains)
                    }
                }
            }

            currentLists.removeAll {
                it.isBuiltIn && it.id !in newBuiltInIds
            }

            saveLists(context, currentLists)
        }
    }

    fun getAllLists(context: Context): MutableList<DomainList> {
        synchronized(lock) {
            return try {
                val json = listsFile(context).readFully().toString(Charsets.UTF_8)
                val type = object : TypeToken<List<DomainList>>() {}.type
                val lists: List<DomainList> = gson.fromJson(json, type) ?: emptyList()
                lists.toMutableList()
            } catch (_: FileNotFoundException) {
                mutableListOf()
            } catch (e: Exception) {
                Log.e(TAG, "Could not read domain lists", e)
                mutableListOf()
            }
        }
    }

    fun getLists(context: Context): List<DomainList> {
        return getAllLists(context).filter { !it.isDeleted }
    }

    fun getActiveDomains(context: Context): List<String> {
        return getLists(context).filter { it.isActive }.flatMap { it.domains }.distinct()
    }

    fun saveLists(context: Context, lists: List<DomainList>) {
        synchronized(lock) {
            val file = listsFile(context)
            val output = file.startWrite()
            try {
                output.write(gson.toJson(lists).toByteArray(Charsets.UTF_8))
                file.finishWrite(output)
            } catch (e: Exception) {
                file.failWrite(output)
                throw e
            }
        }
    }

    fun addList(context: Context, name: String, domains: List<String>): Boolean {
        synchronized(lock) {
            val lists = getAllLists(context)
            val id = name.lowercase().replace(" ", "_")

            val existing = lists.find { it.id == id }

            if (existing != null) {
                if (existing.isBuiltIn && existing.isDeleted) {
                    val index = lists.indexOf(existing)

                    lists[index] = existing.copy(
                        name = name,
                        domains = domains,
                        isDeleted = false,
                        isModified = true,
                        isActive = true
                    )

                    saveLists(context, lists)
                    return true
                }

                return false
            }

            lists.add(
                DomainList(
                    id = id,
                    name = name,
                    domains = domains,
                    isActive = true,
                    isBuiltIn = false
                )
            )

            saveLists(context, lists)
            return true
        }
    }

    fun updateList(context: Context, id: String, name: String, domains: List<String>): Boolean {
        synchronized(lock) {
            val lists = getAllLists(context)
            val index = lists.indexOfFirst { it.id == id }

            if (index == -1) return false

            val oldList = lists[index]

            lists[index] = oldList.copy(
                name = name,
                domains = domains,
                isModified = oldList.isBuiltIn
            )

            saveLists(context, lists)
            return true
        }
    }

    fun toggleListActive(context: Context, id: String): Boolean {
        synchronized(lock) {
            val lists = getAllLists(context)
            val index = lists.indexOfFirst { it.id == id }

            if (index == -1) return false

            val list = lists[index]
            lists[index] = list.copy(isActive = !list.isActive)

            saveLists(context, lists)
            return true
        }
    }

    fun deleteList(context: Context, id: String): Boolean {
        synchronized(lock) {
            val lists = getAllLists(context)
            val index = lists.indexOfFirst { it.id == id }

            if (index == -1) return false

            val list = lists[index]

            if (list.isBuiltIn) {
                lists[index] = list.copy(
                    isDeleted = true,
                    isActive = false
                )
            } else {
                lists.removeAt(index)
            }

            saveLists(context, lists)
            return true
        }
    }

    fun resetLists(context: Context) {
        synchronized(lock) {
            listsFile(context).delete()
            syncLists(context)
        }
    }
}
