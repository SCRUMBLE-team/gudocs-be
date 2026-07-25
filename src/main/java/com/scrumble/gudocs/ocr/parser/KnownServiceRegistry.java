package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory.*;

/**
 * 프론트 CATEGORY_SERVICES와 정확히 같은 한글 서비스명을 canonical name으로 쓴다.
 * "디지니플러스"는 프론트 표기의 오타를 그대로 반영한 것이다(로고 매칭이 이름 문자열 기준이므로 임의 수정 금지).
 */
public final class KnownServiceRegistry {

    public record KnownService(String canonicalName, SubscriptionCategory category, List<String> aliases) {
    }

    private static final List<KnownService> SERVICES = List.of(
            new KnownService("유튜브 프리미엄", OTT, List.of("youtube premium", "유튜브프리미엄")),
            new KnownService("넷플릭스", OTT, List.of("netflix")),
            new KnownService("디지니플러스", OTT, List.of("disney+", "disney plus", "디즈니플러스")),
            new KnownService("티빙", OTT, List.of("tving")),
            new KnownService("쿠팡플레이", OTT, List.of("coupang play")),
            new KnownService("왓챠", OTT, List.of("watcha")),
            new KnownService("웨이브", OTT, List.of("wavve")),
            new KnownService("아마존프라임비디오", OTT, List.of("amazon prime video", "prime video")),
            new KnownService("애플TV", OTT, List.of("apple tv", "appletv")),

            new KnownService("FLO", MUSIC, List.of("플로")),
            new KnownService("유튜브뮤직", MUSIC, List.of("youtube music")),
            new KnownService("스포티파이", MUSIC, List.of("spotify")),
            new KnownService("멜론", MUSIC, List.of("melon")),
            new KnownService("애플뮤직", MUSIC, List.of("apple music")),

            new KnownService("iCloud", CLOUD, List.of("아이클라우드")),
            new KnownService("Google Drive", CLOUD, List.of("구글드라이브", "구글 드라이브")),
            new KnownService("Dropbox", CLOUD, List.of("드롭박스")),
            new KnownService("네이버 클라우드", CLOUD, List.of("naver cloud")),
            new KnownService("OneDrive", CLOUD, List.of("원드라이브")),

            new KnownService("Notion", PRODUCTIVITY, List.of("노션")),
            new KnownService("Microsoft 365", PRODUCTIVITY, List.of("ms365", "office 365")),
            new KnownService("Slack", PRODUCTIVITY, List.of("슬랙")),
            new KnownService("Google Workspace", PRODUCTIVITY, List.of("구글 워크스페이스")),

            new KnownService("ChatGPT", AI, List.of("chatgpt plus", "챗지피티", "openai")),
            new KnownService("Claude", AI, List.of("클로드", "anthropic")),
            new KnownService("Perplexity", AI, List.of("퍼플렉시티")),
            new KnownService("Gemini", AI, List.of("제미나이")),

            new KnownService("NYT", NEWS, List.of("new york times", "뉴욕타임스")),
            new KnownService("Medium", NEWS, List.of("미디엄")),
            new KnownService("퍼블리", NEWS, List.of("publy")),
            new KnownService("롱블랙", NEWS, List.of("long black")),

            new KnownService("인프런", EDUCATION, List.of("inflearn")),
            new KnownService("Udemy", EDUCATION, List.of("유데미")),
            new KnownService("Coursera", EDUCATION, List.of("코세라")),
            new KnownService("클래스101", EDUCATION, List.of("class101")),

            new KnownService("Xbox Game Pass", GAME, List.of("엑스박스 게임패스", "game pass")),
            new KnownService("PS Plus", GAME, List.of("playstation plus", "플레이스테이션 플러스")),
            new KnownService("Nintendo Switch Online", GAME, List.of("닌텐도 스위치 온라인")),

            new KnownService("쿠팡 와우", SHOPPING, List.of("coupang wow", "쿠팡와우")),
            new KnownService("네이버플러스", SHOPPING, List.of("naver plus", "네이버 플러스")),
            new KnownService("SSG.COM 유니버스클럽", SHOPPING, List.of("ssg 유니버스클럽")),

            new KnownService("Figma", DESIGN, List.of("피그마")),
            new KnownService("Adobe CC", DESIGN, List.of("adobe creative cloud", "어도비")),
            new KnownService("Canva", DESIGN, List.of("캔바"))
    );

    private KnownServiceRegistry() {
    }

    public static Optional<KnownService> match(String text) {
        String normalized = normalize(text);
        return SERVICES.stream()
                .filter(service -> matches(service, normalized))
                .findFirst();
    }

    private static boolean matches(KnownService service, String normalizedText) {
        if (normalizedText.contains(normalize(service.canonicalName()))) {
            return true;
        }
        return service.aliases().stream().anyMatch(alias -> normalizedText.contains(normalize(alias)));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
