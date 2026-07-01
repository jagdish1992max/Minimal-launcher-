package com.example.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStreamReader

data class IconPackInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

class IconPackManager(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /**
     * Finds installed icon packs by searching for popular launcher theme categories.
     */
    fun getInstalledIconPacks(): List<IconPackInfo> {
        val iconPacks = mutableListOf<IconPackInfo>()
        val intentActions = listOf(
            "com.novalauncher.THEME",
            "org.adw.launcher.THEMES",
            "com.gau.go.launcherex.theme",
            "com.dlto.customtheme.key"
        )

        val packagesSeen = mutableSetOf<String>()

        for (action in intentActions) {
            val intent = Intent(action)
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in list) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (!packagesSeen.contains(pkgName)) {
                    packagesSeen.add(pkgName)
                    try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        iconPacks.add(IconPackInfo(pkgName, label, icon))
                    } catch (e: Exception) {
                        Log.e("IconPackManager", "Error getting icon pack info for $pkgName", e)
                    }
                }
            }
        }

        // Add packages with "iconpack" or "icon_pack" in their name just in case they lack category declarations
        val allApps = pm.getInstalledApplications(0)
        for (app in allApps) {
            val pkgName = app.packageName
            if (!packagesSeen.contains(pkgName) && (pkgName.contains("iconpack") || pkgName.contains("icon_pack"))) {
                packagesSeen.add(pkgName)
                try {
                    val label = pm.getApplicationLabel(app).toString()
                    val icon = pm.getApplicationIcon(app)
                    iconPacks.add(IconPackInfo(pkgName, label, icon))
                } catch (e: Exception) {
                    Log.e("IconPackManager", "Error getting icon pack info for $pkgName", e)
                }
            }
        }

        return iconPacks
    }

    /**
     * Load the mapping from Component Name to Drawable Name from an icon pack's appfilter.xml
     */
    fun loadIconPackMapping(iconPackPackageName: String): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        try {
            val targetContext = context.createPackageContext(iconPackPackageName, 0)
            val assetManager = targetContext.assets
            var inputStream = try {
                assetManager.open("appfilter.xml")
            } catch (e: Exception) {
                null
            }

            if (inputStream == null) {
                // Try to find it in xml resources of the icon pack
                val resources = pm.getResourcesForApplication(iconPackPackageName)
                val xmlId = resources.getIdentifier("appfilter", "xml", iconPackPackageName)
                if (xmlId != 0) {
                    val xmlParser = resources.getXml(xmlId)
                    var eventType = xmlParser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && xmlParser.name == "item") {
                            val component = xmlParser.getAttributeValue(null, "component")
                            val drawable = xmlParser.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) {
                                mapping[component] = drawable
                            }
                        }
                        eventType = xmlParser.next()
                    }
                    return mapping
                }
            }

            inputStream?.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(InputStreamReader(stream))
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        if (parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (component != null && drawable != null) {
                                mapping[component] = drawable
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            Log.e("IconPackManager", "Error loading appfilter mapping for $iconPackPackageName", e)
        }
        return mapping
    }

    /**
     * Load a custom icon from an icon pack for a specific component.
     * componentName is of format: ComponentInfo{packageName/className}
     */
    fun loadIcon(iconPackPackageName: String, mapping: Map<String, String>, packageName: String, className: String): Drawable? {
        try {
            val key = "ComponentInfo{$packageName/$className}"
            val drawableName = mapping[key] ?: return null
            val resources = pm.getResourcesForApplication(iconPackPackageName)
            val drawableId = resources.getIdentifier(drawableName, "drawable", iconPackPackageName)
            if (drawableId != 0) {
                return resources.getDrawable(drawableId, null)
            }
        } catch (e: Exception) {
            Log.e("IconPackManager", "Error loading custom icon for $packageName/$className from $iconPackPackageName", e)
        }
        return null
    }
}
