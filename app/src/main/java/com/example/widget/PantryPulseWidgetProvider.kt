package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.PantryDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PantryPulseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(appContext)
                    val componentName = ComponentName(appContext, PantryPulseWidgetProvider::class.java)
                    val ids = manager.getAppWidgetIds(componentName)
                    if (ids.isNotEmpty()) {
                        updateWidgetsInternal(appContext, manager, ids)
                    }
                }
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, PantryPulseWidgetProvider::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appWidgetManager.isRequestPinAppWidgetSupported) {
                appWidgetManager.requestPinAppWidget(provider, null, null)
            } else {
                false
            }
        }

        private suspend fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val items = PantryDatabase.getInstance(context).pantryDao().getActiveItemsSnapshot()
            val expiringItems = items.filter { it.effectiveDaysUntilExpiry <= 3 }
            val topItems = (expiringItems.ifEmpty { items }).take(3)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_pantry_pulse)
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                val badgeLabel = if (expiringItems.isEmpty()) {
                    context.getString(R.string.hero_all_fresh_badge)
                } else {
                    context.getString(R.string.hero_expiring_soon_badge, expiringItems.size)
                }
                views.setTextViewText(R.id.widget_urgent_badge, badgeLabel)

                val subtitle = if (expiringItems.isEmpty()) {
                    context.getString(R.string.widget_empty)
                } else {
                    context.getString(R.string.widget_card_expiring_count, expiringItems.size)
                }
                views.setTextViewText(R.id.widget_subtitle, subtitle)

                val itemViewIds = listOf(R.id.widget_item_1, R.id.widget_item_2, R.id.widget_item_3)
                itemViewIds.forEachIndexed { index, viewId ->
                    val item = topItems.getOrNull(index)
                    if (item != null) {
                        val daysLeft = item.effectiveDaysUntilExpiry
                        val dayText = when {
                            daysLeft <= 0 -> context.getString(R.string.expiry_use_today)
                            daysLeft == 1 -> context.getString(R.string.expiry_one_day)
                            else -> context.getString(R.string.expiry_many_days, daysLeft)
                        }
                        views.setViewVisibility(viewId, View.VISIBLE)
                        views.setTextViewText(
                            viewId,
                            "• ${item.name} (${item.quantity}) — $dayText · ${item.formattedExpiryDate}"
                        )
                    } else {
                        views.setViewVisibility(viewId, View.GONE)
                    }
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
