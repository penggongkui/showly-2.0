package com.michaldrabik.ui_progress.calendar.helpers.groupers

import com.michaldrabik.common.extensions.nowUtc
import com.michaldrabik.common.extensions.toLocalZone
import com.michaldrabik.ui_progress.R
import com.michaldrabik.ui_progress.calendar.helpers.CalendarMode
import com.michaldrabik.ui_progress.calendar.recycler.CalendarListItem
import org.threeten.bp.temporal.ChronoUnit.DAYS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRecentsGrouper @Inject constructor() : CalendarGrouper {

  override fun groupByTime(items: List<CalendarListItem.Episode>): List<CalendarListItem> {
    val now = nowUtc().toLocalZone()
    val nowDays = now.truncatedTo(DAYS)

    val todayItems = items.filter {
      val itemDate = it.episode.firstAired?.toLocalZone()
      val itemDays = itemDate?.truncatedTo(DAYS)
      itemDays?.isEqual(nowDays) == true && now.isAfter(itemDate)
    }
    val yesterdayItems = (items - todayItems).filter {
      val itemDays = it.episode.firstAired?.toLocalZone()?.truncatedTo(DAYS)
      itemDays?.isEqual(nowDays.minusDays(1)) == true
    }
    val last7DaysItems = (items - todayItems - yesterdayItems)
      .filter {
        val itemDays = it.episode.firstAired?.toLocalZone()?.truncatedTo(DAYS)
        itemDays?.isAfter(nowDays.minusDays(8)) == true
      }
    val last30DaysItems = (items - todayItems - yesterdayItems - last7DaysItems)
      .filter {
        val itemDays = it.episode.firstAired?.toLocalZone()?.truncatedTo(DAYS)
        itemDays?.isAfter(nowDays.minusDays(31)) == true
      }
    val last90Days = (items - todayItems - yesterdayItems - last7DaysItems - last30DaysItems)
      .filter {
        val itemDays = it.episode.firstAired?.toLocalZone()?.truncatedTo(DAYS)
        itemDays?.isAfter(nowDays.minusDays(91)) == true
      }

    val itemsMap = mutableMapOf<Int, List<CalendarListItem>>()
      .apply {
        put(R.string.textToday, todayItems)
        put(R.string.textYesterday, yesterdayItems)
        put(R.string.textLast7Days, last7DaysItems)
        put(R.string.textLast30Days, last30DaysItems)
        put(R.string.textLast90Days, last90Days)
      }

    return itemsMap.entries.fold(
      mutableListOf(),
      { acc, entry ->
        acc.apply {
          if (entry.value.isNotEmpty()) {
            add(CalendarListItem.Header.create(entry.key, CalendarMode.RECENTS))
            addAll(entry.value)
          }
        }
      }
    )
  }
}
