package com.hero.ziggymusic.presentation.common.sort

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.hero.ziggymusic.data.local.entity.MusicTrackEntity
import com.hero.ziggymusic.domain.music.model.FavoriteMusicTrack
import com.hero.ziggymusic.domain.music.model.MusicTracksSortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class MusicTrackSorter @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
) {
    private enum class MusicTrackTextGroup {
        HANGUL, // 한글
        LATIN, // 영문 알파벳
        OTHER, // 기타 (숫자, 일본어, 중국어 등)
        EMPTY // 제목 또는 아티스트가 비어 있음
    }

    fun sortMusicTracks(
        items: List<MusicTrackEntity>,
        sortOrder: MusicTracksSortOrder,
    ): List<MusicTrackEntity> {
        return sort(
            items = items,
            sortOrder = sortOrder,
            trackSelector = { track -> track },
            dateSelector = { track -> track.dateAdded },
        )
    }

    fun sortFavoriteMusicTracks(
        items: List<FavoriteMusicTrack>,
        sortOrder: MusicTracksSortOrder,
    ): List<FavoriteMusicTrack> {
        return sort(
            items = items,
            sortOrder = sortOrder,
            trackSelector = { favorite -> favorite.track },

            // 즐겨찾기의 날짜 정렬은 MediaStore 등록일이 아니라 즐겨찾기 추가일을 사용한다.
            dateSelector = { favorite -> favorite.addedToFavoritesAt },
        )
    }

    fun currentLocaleTag(): String {
        return getCurrentLocale().toLanguageTag()
    }

    private fun <T> sort(
        items: List<T>,
        sortOrder: MusicTracksSortOrder,
        trackSelector: (T) -> MusicTrackEntity,
        dateSelector: (T) -> Long,
    ): List<T> {
        val locale = getCurrentLocale()
        val collator = createCollator(locale)

        return items.sortedWith { firstItem, secondItem ->
            val firstTrack = trackSelector(firstItem)
            val secondTrack = trackSelector(secondItem)

            val primaryResult = when {
                sortOrder.isTitleOrder -> {
                    compareMusicTrackText(
                        first = firstTrack.title,
                        second = secondTrack.title,
                        descending = sortOrder.isDescending,
                        locale = locale,
                        collator = collator,
                    )
                }

                sortOrder.isArtistOrder -> {
                    compareMusicTrackText(
                        first = firstTrack.artist,
                        second = secondTrack.artist,
                        descending = sortOrder.isDescending,
                        locale = locale,
                        collator = collator,
                    )
                }

                else -> {
                    compareDateAdded(
                        first = dateSelector(firstItem),
                        second = dateSelector(secondItem),
                        descending = sortOrder.isDescending,
                    )
                }
            }

            if (primaryResult != 0) {
                primaryResult
            } else {
                /*
                 * 동일한 제목·아티스트·날짜가 있어도 RecyclerView와 재생 큐의
                 * 순서가 매번 달라지지 않도록 보조 정렬 키와 ID를 비교한다.
                 */
                compareBySecondarySortKeys(
                    first = firstTrack,
                    second = secondTrack,
                    sortOrder = sortOrder,
                    locale = locale,
                    collator = collator,
                )
            }
        }
    }

    private fun compareBySecondarySortKeys(
        first: MusicTrackEntity,
        second: MusicTrackEntity,
        sortOrder: MusicTracksSortOrder,
        locale: Locale,
        collator: Collator,
    ): Int {
        return when {
            sortOrder.isTitleOrder -> {
                /*
                 * 제목이 같으면 아티스트를 같은 정렬 방향으로 비교한다.
                 * 아티스트도 같으면 ID 오름차순으로 결정적 순서를 만든다.
                 */
                val artistResult = compareMusicTrackText(
                    first = first.artist,
                    second = second.artist,
                    descending = sortOrder.isDescending,
                    locale = locale,
                    collator = collator,
                )

                if (artistResult != 0) {
                    artistResult
                } else {
                    first.id.compareTo(second.id)
                }
            }

            sortOrder.isArtistOrder -> {
                /*
                 * 아티스트가 같으면 제목을 같은 정렬 방향으로 비교한다.
                 * 제목도 같으면 ID 오름차순으로 결정적 순서를 만든다.
                 */
                val titleResult = compareMusicTrackText(
                    first = first.title,
                    second = second.title,
                    descending = sortOrder.isDescending,
                    locale = locale,
                    collator = collator,
                )

                if (titleResult != 0) {
                    titleResult
                } else {
                    first.id.compareTo(second.id)
                }
            }

            else -> {
                /*
                 * 같은 날짜에서는 정렬 방향과 관계없이
                 * 제목 → 아티스트 → ID 오름차순으로 순서를 고정한다.
                 */
                val titleResult = compareMusicTrackText(
                    first = first.title,
                    second = second.title,
                    descending = false,
                    locale = locale,
                    collator = collator,
                )

                if (titleResult != 0) {
                    titleResult
                } else {
                    val artistResult = compareMusicTrackText(
                        first = first.artist,
                        second = second.artist,
                        descending = false,
                        locale = locale,
                        collator = collator,
                    )

                    if (artistResult != 0) {
                        artistResult
                    } else {
                        first.id.compareTo(second.id)
                    }
                }
            }
        }
    }

    private fun compareDateAdded(
        first: Long,
        second: Long,
        descending: Boolean,
    ): Int {
        // 날짜를 알 수 없는 항목은 정렬 방향과 관계없이 마지막에 둔다.
        val firstHasDateAdded = first > 0L
        val secondHasDateAdded = second > 0L

        // 날짜가 없는 항목은 정렬 방향과 관계없이 항상 마지막에 둔다.
        if (firstHasDateAdded != secondHasDateAdded) {
            return if (firstHasDateAdded) {
                -1
            } else {
                1
            }
        }

        return if (descending) {
            second.compareTo(first)
        } else {
            first.compareTo(second)
        }
    }

    private fun compareMusicTrackText(
        first: String?,
        second: String?,
        descending: Boolean,
        locale: Locale,
        collator: Collator,
    ): Int {
        val firstText = normalizeMusicTrackText(first)
        val secondText = normalizeMusicTrackText(second)

        val firstGroup = getMusicTrackTextGroup(firstText)
        val secondGroup = getMusicTrackTextGroup(secondText)

        val firstGroupRank = getMusicTrackTextGroupRank(
            group = firstGroup,
            locale = locale,
            descending = descending,
        )

        val secondGroupRank = getMusicTrackTextGroupRank(
            group = secondGroup,
            locale = locale,
            descending = descending,
        )

        val groupResult = firstGroupRank.compareTo(secondGroupRank)

        if (groupResult != 0) {
            return groupResult
        }

        val textResult = collator.compare(
            firstText,
            secondText,
        )

        return if (descending) {
            -textResult
        } else {
            textResult
        }
    }

    private fun normalizeMusicTrackText(
        text: String?,
    ): String {
        return Normalizer.normalize(
            text?.trim().orEmpty(),
            Normalizer.Form.NFC,
        )
    }

    private fun getMusicTrackTextGroup(
        text: String,
    ): MusicTrackTextGroup {
        if (text.isBlank()) {
            return MusicTrackTextGroup.EMPTY
        }

        /*
         * 제목이나 아티스트 앞에 괄호 등의 기호가 있어도 해당 기호로
         * 그룹을 판별하지 않고 첫 번째 문자 또는 숫자를 사용한다.
         */
        val firstMeaningfulCharacter =
            text.firstOrNull { character ->
                Character.isLetterOrDigit(character)
            } ?: return MusicTrackTextGroup.OTHER

        return when (
            Character.UnicodeScript.of(
                firstMeaningfulCharacter.code,
            )
        ) {
            Character.UnicodeScript.HANGUL ->
                MusicTrackTextGroup.HANGUL

            Character.UnicodeScript.LATIN ->
                MusicTrackTextGroup.LATIN

            else ->
                MusicTrackTextGroup.OTHER
        }
    }

    private fun getMusicTrackTextGroupRank(
        group: MusicTrackTextGroup,
        locale: Locale,
        descending: Boolean,
    ): Int {
        return when (group) {
            MusicTrackTextGroup.EMPTY -> 3
            MusicTrackTextGroup.OTHER -> 2

            MusicTrackTextGroup.HANGUL,
            MusicTrackTextGroup.LATIN,
                -> {
                getHangulAndLatinGroupRank(
                    group = group,
                    locale = locale,
                    descending = descending,
                )
            }
        }
    }

    private fun getHangulAndLatinGroupRank(
        group: MusicTrackTextGroup,
        locale: Locale,
        descending: Boolean,
    ): Int {
        /*
         * 한국어와 영어에서는 현재 앱 언어의 문자 그룹을 우선한다.
         * 내림차순에서는 한글과 영문의 그룹 순서도 함께 뒤집는다.
         */
        val hangulFirst = when (locale.language) {
            Locale.KOREAN.language -> {
                /*
                 * 한국어
                 * 오름차순: 한글 → 영문
                 * 내림차순: 영문 → 한글
                 */
                !descending
            }

            Locale.ENGLISH.language -> {
                /*
                 * 영어
                 * 오름차순: 영문 → 한글
                 * 내림차순: 한글 → 영문
                 */
                descending
            }

            else -> {
                /*
                 * 별도 정책이 없는 언어에서는 한글과 영문에 같은 그룹 순위를 주고
                 * 현재 언어의 Collator가 실제 순서를 결정하게 한다.
                 */
                return 0
            }
        }

        return when (group) {
            MusicTrackTextGroup.HANGUL ->
                if (hangulFirst) 0 else 1

            MusicTrackTextGroup.LATIN ->
                if (hangulFirst) 1 else 0

            else -> 2
        }
    }

    private fun createCollator(
        locale: Locale,
    ): Collator {
        return Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    private fun getCurrentLocale(): Locale {
        val locales = ConfigurationCompat.getLocales(
            context.resources.configuration,
        )

        return if (locales.isEmpty) {
            Locale.getDefault()
        } else {
            locales[0] ?: Locale.getDefault()
        }
    }
}