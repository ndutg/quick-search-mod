package com.tk.quicksearch.pinnedNotifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tk.quicksearch.R
import com.tk.quicksearch.widgets.customButtonsWidget.CustomWidgetButtonAction
import com.tk.quicksearch.widgets.customButtonsWidget.WidgetActionActivity
import com.tk.quicksearch.widgets.customButtonsWidget.rememberWidgetButtonIcon
import org.json.JSONArray

object PinnedNotifications {
    private const val PreferencesName = "pinned_notification_items"
    private const val ActionsKey = "actions_json"
    private const val ChannelId = "pinned_shortcuts"
    private const val NotificationId = 7_300
    private const val ItemsPerRow = 4

    fun pin(context: Context, action: CustomWidgetButtonAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            context.startActivity(PinnedNotificationPermissionActivity.createIntent(context, action))
            return
        }
        save(context, actions(context) + action)
        show(context)
    }

    fun isPinned(context: Context, action: CustomWidgetButtonAction): Boolean =
        actions(context).any { it.toJson() == action.toJson() }

    fun pinnedItems(context: Context): List<CustomWidgetButtonAction> = actions(context)

    fun remove(context: Context, action: CustomWidgetButtonAction) {
        save(context, actions(context).filterNot { it.toJson() == action.toJson() })
        show(context)
    }

    fun reorder(context: Context, actions: List<CustomWidgetButtonAction>) {
        save(context, actions)
        show(context)
    }

    fun toggle(context: Context, action: CustomWidgetButtonAction) {
        if (isPinned(context, action)) {
            remove(context, action)
        } else {
            pin(context, action)
        }
    }

    fun completePermissionRequest(context: Context, action: CustomWidgetButtonAction) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            save(context, actions(context) + action)
            show(context)
        }
    }

    fun show(context: Context) {
        val actions = actions(context)
        if (actions.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NotificationId)
            return
        }
        createChannel(context)
        val compact = buildRemoteViews(context, actions.take(ItemsPerRow), showLabels = false)
        val expanded = buildRemoteViews(context, actions, showLabels = true)
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_pin)
            .setContentTitle(context.getString(R.string.pinned_notifications_title))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    NotificationId,
                    Intent(context, PinnedNotificationRestoreReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .build()
        NotificationManagerCompat.from(context).notify(NotificationId, notification)
    }

    private fun buildRemoteViews(
        context: Context,
        actions: List<CustomWidgetButtonAction>,
        showLabels: Boolean,
    ): RemoteViews {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return RemoteViews(context.packageName, R.layout.notification_pinned_items)
        }
        return RemoteViews(context.packageName, R.layout.notification_pinned_items).apply {
            actions.chunked(ItemsPerRow).forEachIndexed { rowIndex, rowActions ->
                val row = RemoteViews(context.packageName, R.layout.notification_pinned_row)
                rowActions.forEachIndexed { columnIndex, action ->
                    row.addView(
                        R.id.pinned_notification_row,
                        itemView(context, action, rowIndex * ItemsPerRow + columnIndex, showLabels),
                    )
                }
                addView(R.id.pinned_notification_rows, row)
            }
        }
    }

    private fun itemView(
        context: Context,
        action: CustomWidgetButtonAction,
        index: Int,
        showLabels: Boolean,
    ): RemoteViews {
        val iconSize = (context.resources.displayMetrics.density * 32).toInt()
        val icon = rememberWidgetButtonIcon(context, action, iconSize, Color.White, null)
        return RemoteViews(context.packageName, R.layout.notification_pinned_item).apply {
            notificationIconBitmap(context, action, icon.bitmap, icon.drawableResId, iconSize)?.let {
                setImageViewBitmap(R.id.pinned_notification_icon, it)
            }
                ?: icon.drawableResId?.let { setImageViewResource(R.id.pinned_notification_icon, it) }
            setTextViewText(R.id.pinned_notification_label, action.displayLabel())
            setViewVisibility(
                R.id.pinned_notification_label,
                if (showLabels) android.view.View.VISIBLE else android.view.View.GONE,
            )
            val launchIntent = WidgetActionActivity.createIntent(context, action).apply {
                data = android.net.Uri.parse("quicksearch://pinned-notification/$index/${action.toJson().hashCode()}")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                index,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.pinned_notification_icon, pendingIntent)
            setOnClickPendingIntent(R.id.pinned_notification_label, pendingIntent)
        }
    }

    private fun notificationIconBitmap(
        context: Context,
        action: CustomWidgetButtonAction,
        bitmap: android.graphics.Bitmap?,
        drawableResId: Int?,
        iconSize: Int,
    ): android.graphics.Bitmap? {
        if (bitmap != null) return bitmap
        val isDarkMode =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (action !is CustomWidgetButtonAction.Note || !isDarkMode || drawableResId == null) return null
        return ContextCompat.getDrawable(context, drawableResId)
            ?.mutate()
            ?.apply { setTint(0xFFDCDCDC.toInt()) }
            ?.toBitmap(width = iconSize, height = iconSize)
    }

    private fun actions(context: Context): List<CustomWidgetButtonAction> =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getString(ActionsKey, null)
            ?.let { raw -> runCatching { JSONArray(raw) }.getOrNull() }
            ?.let { array -> List(array.length()) { index -> CustomWidgetButtonAction.fromJson(array.optString(index)) } }
            ?.filterNotNull()
            .orEmpty()

    private fun save(context: Context, actions: List<CustomWidgetButtonAction>) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(ActionsKey, JSONArray(actions.map { it.toJson() }).toString())
            .apply()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ChannelId,
                context.getString(R.string.pinned_notifications_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.pinned_notifications_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
