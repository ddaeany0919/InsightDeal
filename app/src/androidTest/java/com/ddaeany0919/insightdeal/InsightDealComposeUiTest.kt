package com.ddaeany0919.insightdeal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddaeany0919.insightdeal.presentation.TestPushState
import com.ddaeany0919.insightdeal.presentation.settings.SettingsScreen
import com.ddaeany0919.insightdeal.presentation.theme.InsightDealTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wave 2 Android Jetpack Compose 계측 UI 테스트 (`connectedDebugAndroidTest`).
 * 실제 Compose 런타임 상에서 SettingsScreen의 핵심 네비게이션·아코디언·테스트 푸시 콜백을 검증합니다.
 */
@RunWith(AndroidJUnit4::class)
class InsightDealComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_rendersSectionsAndHandlesCallbacks() {
        var backClicks = 0
        var keywordManagerClicks = 0
        var testPushClicks = 0

        composeTestRule.setContent {
            InsightDealTheme {
                SettingsScreen(
                    onBackClick = { backClicks++ },
                    onNavigateToKeywordManager = { keywordManagerClicks++ },
                    testPushState = TestPushState(),
                    onSendTestPush = { testPushClicks++ },
                    onClearTestPushFeedback = {}
                )
            }
        }

        composeTestRule.onNodeWithText("설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("앱 설정").assertIsDisplayed()
        composeTestRule.onNodeWithText("알림 키워드 관리").assertIsDisplayed().performClick()
        assertEquals(1, keywordManagerClicks)

        composeTestRule.onNodeWithText("테스트 푸시 알림 발송").assertIsDisplayed().performClick()
        assertEquals(1, testPushClicks)

        composeTestRule.onNodeWithContentDescription("뒤로가기").performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun settingsScreen_expandsPlatformFilterAccordionToShowPpomppuOverseas() {
        composeTestRule.setContent {
            InsightDealTheme {
                SettingsScreen(
                    onBackClick = {},
                    onNavigateToKeywordManager = {},
                    testPushState = TestPushState(
                        feedbackSeq = 1,
                        message = "테스트 푸시 요청을 완료했어요."
                    ),
                    onSendTestPush = {},
                    onClearTestPushFeedback = {}
                )
            }
        }

        composeTestRule.onNodeWithText("테스트 푸시 요청을 완료했어요.").assertIsDisplayed()
        composeTestRule.onNodeWithText("표시할 커뮤니티 출처 선택").performClick()
        composeTestRule.onNodeWithText("뽐뿌해외").assertIsDisplayed()
        composeTestRule.onNodeWithText("빠삭육아").assertIsDisplayed()
    }
}
