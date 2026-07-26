package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory.*;

/**
 * canonical name은 프론트 로고 매칭 키로 쓰인다.
 * "디지니플러스"는 프론트 표기의 오타를 그대로 반영한 것이다(로고 매칭이 이름 문자열 기준이므로 임의 수정 금지).
 * 국내 이용자가 많이 쓰는 서비스 위주로 이 목록이 기준(source of truth)이며, 신규 추가분은 프론트 CATEGORY_SERVICES에도 동기화되어야 한다.
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
            new KnownService("라프텔", OTT, List.of("laftel")),

            new KnownService("FLO", MUSIC, List.of("플로")),
            new KnownService("유튜브뮤직", MUSIC, List.of("youtube music")),
            new KnownService("스포티파이", MUSIC, List.of("spotify")),
            new KnownService("멜론", MUSIC, List.of("melon")),
            new KnownService("애플뮤직", MUSIC, List.of("apple music")),
            new KnownService("지니뮤직", MUSIC, List.of("genie music", "지니")),
            new KnownService("벅스", MUSIC, List.of("bugs", "벅스뮤직")),

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
            new KnownService("뤼튼", AI, List.of("wrtn")),
            new KnownService("클로바X", AI, List.of("clova x", "클로바엑스")),

            new KnownService("NYT", NEWS, List.of("new york times", "뉴욕타임스")),
            new KnownService("Medium", NEWS, List.of("미디엄")),
            new KnownService("퍼블리", NEWS, List.of("publy")),
            new KnownService("롱블랙", NEWS, List.of("long black")),
            new KnownService("아웃스탠딩", NEWS, List.of("outstanding")),

            new KnownService("인프런", EDUCATION, List.of("inflearn")),
            new KnownService("Udemy", EDUCATION, List.of("유데미")),
            new KnownService("Coursera", EDUCATION, List.of("코세라")),
            new KnownService("클래스101", EDUCATION, List.of("class101")),
            new KnownService("탈잉", EDUCATION, List.of("taling")),
            new KnownService("야나두", EDUCATION, List.of("yanadoo")),
            new KnownService("링글", EDUCATION, List.of("ringle")),
            new KnownService("스픽", EDUCATION, List.of("speak")),

            new KnownService("Xbox Game Pass", GAME, List.of("엑스박스 게임패스", "game pass")),
            new KnownService("PS Plus", GAME, List.of("playstation plus", "플레이스테이션 플러스")),
            new KnownService("Nintendo Switch Online", GAME, List.of("닌텐도 스위치 온라인")),
            new KnownService("EA Play", GAME, List.of("ea play")),

            new KnownService("쿠팡 와우", SHOPPING, List.of("coupang wow", "쿠팡와우")),
            new KnownService("네이버플러스", SHOPPING, List.of("naver plus", "네이버 플러스")),
            new KnownService("SSG.COM 유니버스클럽", SHOPPING, List.of("ssg 유니버스클럽")),
            new KnownService("배민클럽", SHOPPING, List.of("우아한형제들", "배달의민족", "baemin club")),
            new KnownService("요기패스", SHOPPING, List.of("yogiyo", "요기요")),

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

    // ponytail: contains()로 짧은 alias(FLO, NYT 등)가 무관한 텍스트의 부분 문자열과 오매칭될 수 있음.
    // best-effort 파서라 사용자가 결과를 확인/수정하므로 감내 가능한 리스크로 판단. 오탐이 실제 문제가 되면
    // 단어 경계(\b) 매칭으로 전환.
    private static boolean matches(KnownService service, String normalizedText) {
        if (normalizedText.contains(normalize(service.canonicalName()))) {
            return true;
        }
        return service.aliases().stream().anyMatch(alias -> normalizedText.contains(normalize(alias)));
    }

    // 괄호/언더스코어 등 구두점이 섞인 영수증 표기("쿠팡(와우 멤버십)")도 alias와 매칭되도록 문자/숫자만 남긴다.
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
