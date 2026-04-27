package by.siaroa.playfarmscoreboard.client.hud;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomHudPlaceholderResolverParsingTest {
    private static Method parseFlyMethod;
    private static Method parseAutoPlantMethod;

    @BeforeAll
    static void prepare() throws NoSuchMethodException {
        parseFlyMethod = CustomHudPlaceholderResolver.class.getDeclaredMethod("parseFlyText", String.class);
        parseFlyMethod.setAccessible(true);
        parseAutoPlantMethod = CustomHudPlaceholderResolver.class.getDeclaredMethod("parseAutoPlantText", String.class);
        parseAutoPlantMethod.setAccessible(true);
    }

    @Test
    void parseAutoPlantFromCommandFeedback() throws Exception {
        String message = "ꕜ 자동심기를 활성화했어요. (남은 횟수: 223216회)";
        assertEquals("223,216", parseAutoPlant(message));
    }

    @Test
    void parseAutoPlantFromAliasFeedback() throws Exception {
        String message = "ꕜ 자심 비활성화했어요. (남은 횟수: 1200회)";
        assertEquals("1,200", parseAutoPlant(message));
    }

    @Test
    void ignoreAutoPlantWhenMessageIsDifferent() throws Exception {
        String message = "ꕜ 자동심기 준비가 완료되었습니다.";
        assertEquals("", parseAutoPlant(message));
    }

    @Test
    void parseFlyFromCommandFeedback() throws Exception {
        String message = "ꕜ 플라이를 활성화했어요. (남은 시간: 3시간 48분 57초)";
        assertEquals("3시간 48분 57초", parseFly(message));
    }

    @Test
    void parseFlyRemainingRawWhenNoTimeUnit() throws Exception {
        String message = "ꕜ 플라이를 비활성화했어요. (남은 시간: 223216)";
        assertEquals("223216", parseFly(message));
    }

    @Test
    void ignoreFlyWhenMessageIsDifferent() throws Exception {
        String message = "ꕜ 플라이 설정이 변경되었습니다.";
        assertEquals("", parseFly(message));
    }

    private static String parseFly(String raw) throws InvocationTargetException, IllegalAccessException {
        return (String) parseFlyMethod.invoke(null, raw);
    }

    private static String parseAutoPlant(String raw) throws InvocationTargetException, IllegalAccessException {
        return (String) parseAutoPlantMethod.invoke(null, raw);
    }
}
