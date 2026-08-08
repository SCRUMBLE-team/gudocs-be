package com.scrumble.gudocs.subscriptions.catalog;

import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.scrumble.gudocs.subscriptions.entity.BillingCycle.MONTHLY;
import static com.scrumble.gudocs.subscriptions.entity.BillingCycle.YEARLY;
import static com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory.*;

/**
 * 국내 이용자가 많이 쓰는 구독 서비스 목록과 요금제를 담은 카탈로그. 두 곳에서 쓰인다.
 * <ul>
 *     <li>구독 등록 화면 — {@code GET /api/subscriptions/catalog}로 내려보내 서비스 선택 시 요금을 자동 채운다.</li>
 *     <li>OCR 파싱 — 영수증 텍스트에서 서비스명·카테고리를 인식하고, 금액으로 요금제명을 역추적한다.</li>
 * </ul>
 *
 * <p><b>이 목록이 서비스 데이터의 단일 소스(source of truth)다.</b> 프론트는 목록을 따로 들지 않고
 * 카탈로그 API로 받아 쓰며, 프론트가 가지는 것은 로고 이미지 파일뿐이다.
 *
 * <p>둘을 잇는 키는 {@code code}(UPPER_SNAKE)다 — 프론트는 로고 파일명을 code로 맞춘다.
 * <b>code는 한번 정하면 절대 바꾸지 않는다.</b> 바꾸는 순간 프론트 로고 매칭이 깨진다.
 * 반대로 {@code canonicalName}은 순수 표시용이라 오타 수정·브랜드 변경으로 자유롭게 고칠 수 있다
 * (표시 이름을 조인 키로 쓰다가 "디지니플러스" 오타 수정 때 양쪽이 어긋난 적이 있어 code를 도입했다).
 * 이름은 한글 표기가 원칙이지만, 브랜드가 영문으로 더 널리 통용되는 서비스(Notion, Figma, ChatGPT, Claude,
 * Gemini, Slack, Google Workspace, Adobe CC, Canva, NYT, Medium 등)는 영문 브랜드명을 그대로 쓴다
 * — 한글 표기는 alias로 등록한다.
 *
 * <p>요금은 {@link #PRICES_CHECKED_ON} 시점에 공개 자료로 확인한 국내 원화 정가다. 실시간 조회가 아니므로
 * 인상·개편이 있으면 이 파일을 고쳐 재배포해야 한다. 사용자는 등록 화면에서 값을 수정할 수 있고,
 * 할인·프로모션·구 요금제 적용 사용자도 있으므로 이 값은 어디까지나 기본값(참고값)이다.
 * 신뢰할 만한 원화 가격을 확인하지 못한 서비스는 추측해서 채우지 않고 {@code plans}를 비워 둔다
 * (해외 결제라 원화 정가가 없는 서비스, 종료된 서비스, 구독이 아니라 건별 구매인 서비스 포함).
 */
public final class ServiceCatalog {

    /** 아래 요금을 공개 자료로 확인한 날짜. API 응답에 함께 내려 사용자가 기준 시점을 알 수 있게 한다. */
    public static final LocalDate PRICES_CHECKED_ON = LocalDate.of(2026, 8, 8);

    public record Plan(String name, Long price, BillingCycle billingCycle) {
    }

    /**
     * @param code       프론트 로고 매칭 키. UPPER_SNAKE, 불변.
     * @param selectable 신규 구독 등록 대상으로 고를 수 있는지. false 면 등록 화면 선택지에서 빠지고
     *                   서버도 이 code 로 들어온 등록을 거부한다. 다만 과거 영수증을 계속 인식해야 하므로
     *                   OCR 매칭 대상으로는 남는다. (종료된 서비스, 독립 구독 상품이 아닌 서비스)
     */
    public record CatalogService(String code, String canonicalName, SubscriptionCategory category,
                                 boolean selectable, List<String> aliases, List<Plan> plans) {

        /** OCR로 읽은 금액과 정확히 일치하는 요금제를 찾는다. 없으면 비어 있는 값. */
        public Optional<Plan> planByPrice(Long price) {
            if (price == null) {
                return Optional.empty();
            }
            return plans.stream().filter(plan -> plan.price().equals(price)).findFirst();
        }
    }

    private static CatalogService service(String code, String name, SubscriptionCategory category,
                                          List<String> aliases, List<Plan> plans) {
        return new CatalogService(code, name, category, true, aliases, plans);
    }

    /**
     * OCR 인식 전용 항목. 신규 등록 대상이 아니므로 요금제를 두지 않는다.
     * 서비스가 종료됐거나, 살아 있어도 독립적으로 결제하는 구독 상품이 아닌 경우다.
     */
    private static CatalogService ocrOnly(String code, String name, SubscriptionCategory category,
                                          List<String> aliases) {
        return new CatalogService(code, name, category, false, aliases, List.of());
    }

    private static final List<CatalogService> SERVICES = List.of(
            service("YOUTUBE_PREMIUM", "유튜브 프리미엄", OTT, List.of("youtube premium", "유튜브프리미엄"),
                    List.of(new Plan("프리미엄 라이트", 8500L, MONTHLY),
                            new Plan("프리미엄", 14900L, MONTHLY),
                            new Plan("프리미엄 (iOS 결제)", 19500L, MONTHLY))),
            service("NETFLIX", "넷플릭스", OTT, List.of("netflix"),
                    List.of(new Plan("광고형 스탠다드", 7000L, MONTHLY),
                            new Plan("스탠다드", 13500L, MONTHLY),
                            new Plan("프리미엄", 17000L, MONTHLY))),
            service("DISNEY_PLUS", "디즈니플러스", OTT, List.of("disney+", "disney plus", "디지니플러스"),
                    List.of(new Plan("스탠다드", 9900L, MONTHLY),
                            new Plan("프리미엄", 13900L, MONTHLY))),
            service("TVING", "티빙", OTT, List.of("tving"),
                    List.of(new Plan("광고형 스탠다드", 5500L, MONTHLY),
                            new Plan("베이직", 9500L, MONTHLY),
                            new Plan("스탠다드", 13500L, MONTHLY),
                            new Plan("프리미엄", 17000L, MONTHLY))),
            // 쿠팡플레이 기본 시청은 와우 멤버십에 포함된다 — 그 금액은 COUPANG_WOW 한 곳에만 둔다.
            // 여기 요금제는 와우와 별개로 결제하는 쿠팡플레이 자체 유료 상품이다.
            service("COUPANG_PLAY", "쿠팡플레이", OTT, List.of("coupang play"),
                    List.of(new Plan("스포츠 패스 (와우회원)", 12400L, MONTHLY),
                            new Plan("스포츠 패스 (일반회원)", 19300L, MONTHLY))),
            service("WATCHA", "왓챠", OTT, List.of("watcha"),
                    List.of(new Plan("베이직", 7900L, MONTHLY),
                            new Plan("프리미엄", 12900L, MONTHLY))),
            service("WAVVE", "웨이브", OTT, List.of("wavve"),
                    List.of(new Plan("광고형 스탠다드", 5500L, MONTHLY),
                            new Plan("베이직", 7900L, MONTHLY),
                            new Plan("스탠다드", 10900L, MONTHLY),
                            new Plan("프리미엄", 13900L, MONTHLY))),
            service("AMAZON_PRIME_VIDEO", "아마존프라임비디오", OTT, List.of("amazon prime video", "prime video"),
                    List.of(new Plan("프라임 비디오", 5500L, MONTHLY))),
            service("APPLE_TV", "애플TV", OTT, List.of("apple tv", "appletv"),
                    List.of(new Plan("월간 구독", 6500L, MONTHLY))),
            service("LAFTEL", "라프텔", OTT, List.of("laftel"),
                    List.of(new Plan("베이직", 9900L, MONTHLY),
                            new Plan("프리미엄", 14900L, MONTHLY))),

            service("FLO", "FLO", MUSIC, List.of("플로"),
                    List.of(new Plan("무제한 듣기", 7900L, MONTHLY))),
            service("YOUTUBE_MUSIC", "유튜브뮤직", MUSIC, List.of("youtube music"),
                    List.of(new Plan("뮤직 프리미엄", 11900L, MONTHLY))),
            service("SPOTIFY", "스포티파이", MUSIC, List.of("spotify"),
                    List.of(new Plan("베이직", 8690L, MONTHLY),
                            new Plan("개인", 11990L, MONTHLY),
                            new Plan("듀오", 17985L, MONTHLY),
                            new Plan("학생", 6600L, MONTHLY))),
            service("MELON", "멜론", MUSIC, List.of("melon"),
                    List.of(new Plan("모바일 스트리밍", 7590L, MONTHLY),
                            new Plan("스트리밍 클럽", 8690L, MONTHLY),
                            new Plan("스트리밍 플러스", 11990L, MONTHLY))),
            service("APPLE_MUSIC", "애플뮤직", MUSIC, List.of("apple music"),
                    List.of(new Plan("개인", 8900L, MONTHLY),
                            new Plan("가족", 13500L, MONTHLY))),
            service("GENIE_MUSIC", "지니뮤직", MUSIC, List.of("genie music", "지니"),
                    List.of(new Plan("스마트 음악감상", 8140L, MONTHLY),
                            new Plan("음악감상 (PC+모바일)", 9240L, MONTHLY),
                            new Plan("초고음질 무제한", 15400L, MONTHLY))),
            service("BUGS", "벅스", MUSIC, List.of("bugs", "벅스뮤직"),
                    List.of(new Plan("무제한 듣기", 8690L, MONTHLY))),

            service("ICLOUD", "iCloud", CLOUD, List.of("아이클라우드"),
                    List.of(new Plan("50GB", 1100L, MONTHLY),
                            new Plan("200GB", 4400L, MONTHLY),
                            new Plan("2TB", 14000L, MONTHLY),
                            new Plan("6TB", 44000L, MONTHLY))),
            // Google One 요금제(구글 드라이브 저장용량 = Google One 구독).
            service("GOOGLE_DRIVE", "Google Drive", CLOUD, List.of("구글드라이브", "구글 드라이브", "google one", "구글 원"),
                    List.of(new Plan("베이직 100GB", 2400L, MONTHLY),
                            new Plan("AI Plus 2TB", 11900L, MONTHLY),
                            new Plan("AI Pro 5TB", 29000L, MONTHLY))),
            service("DROPBOX", "Dropbox", CLOUD, List.of("드롭박스"), List.of()),
            service("NAVER_CLOUD", "네이버 클라우드", CLOUD, List.of("naver cloud", "마이박스", "mybox"),
                    List.of(new Plan("80GB", 1650L, MONTHLY),
                            new Plan("180GB", 3300L, MONTHLY),
                            new Plan("330GB", 5500L, MONTHLY),
                            new Plan("2TB", 11000L, MONTHLY))),
            // OneDrive 100GB 단독 구독은 현재 "Microsoft 365 Basic" 이라는 이름으로 판매된다.
            service("ONEDRIVE", "OneDrive", CLOUD, List.of("원드라이브"),
                    List.of(new Plan("Microsoft 365 Basic (100GB)", 2900L, MONTHLY),
                            new Plan("Microsoft 365 Basic (100GB, 연간)", 29900L, YEARLY))),

            service("NOTION", "Notion", PRODUCTIVITY, List.of("노션"),
                    List.of(new Plan("플러스", 16800L, MONTHLY),
                            new Plan("비즈니스", 36000L, MONTHLY))),
            service("MICROSOFT_365", "Microsoft 365", PRODUCTIVITY, List.of("ms365", "office 365"),
                    List.of(new Plan("Personal", 12500L, MONTHLY),
                            new Plan("Personal (연간)", 125000L, YEARLY),
                            new Plan("Family", 15500L, MONTHLY),
                            new Plan("Family (연간)", 155000L, YEARLY))),
            service("SLACK", "Slack", PRODUCTIVITY, List.of("슬랙"), List.of()),
            service("GOOGLE_WORKSPACE", "Google Workspace", PRODUCTIVITY, List.of("구글 워크스페이스"), List.of()),

            service("CHATGPT", "ChatGPT", AI, List.of("chatgpt plus", "챗지피티", "openai"),
                    List.of(new Plan("Go", 15000L, MONTHLY),
                            new Plan("Plus", 29000L, MONTHLY),
                            new Plan("Pro", 159000L, MONTHLY))),
            service("CLAUDE", "Claude", AI, List.of("클로드", "anthropic"),
                    List.of(new Plan("Pro", 28000L, MONTHLY))),
            service("PERPLEXITY", "Perplexity", AI, List.of("퍼플렉시티"),
                    List.of(new Plan("Pro", 28000L, MONTHLY))),
            service("GEMINI", "Gemini", AI, List.of("제미나이"),
                    List.of(new Plan("Google AI Plus", 11000L, MONTHLY),
                            new Plan("Google AI Pro", 29000L, MONTHLY),
                            new Plan("Google AI Ultra", 119000L, MONTHLY))),
            // 뤼튼은 개인 사용자 무료 정책이라 유료 요금제가 없다.
            service("WRTN", "뤼튼", AI, List.of("wrtn"), List.of()),
            // 클로바X는 2026-04-09 개인 서비스 종료.
            ocrOnly("CLOVA_X", "클로바X", AI, List.of("clova x", "클로바엑스")),

            service("NYT", "NYT", NEWS, List.of("new york times", "뉴욕타임스"), List.of()),
            service("MEDIUM", "Medium", NEWS, List.of("미디엄"), List.of()),
            service("PUBLY", "퍼블리", NEWS, List.of("publy"), List.of()),
            service("LONG_BLACK", "롱블랙", NEWS, List.of("long black"),
                    List.of(new Plan("오늘의 노트", 5900L, MONTHLY),
                            new Plan("무제한 노트", 9900L, MONTHLY))),
            service("OUTSTANDING", "아웃스탠딩", NEWS, List.of("outstanding"),
                    List.of(new Plan("멤버십", 13900L, MONTHLY))),

            service("INFLEARN", "인프런", EDUCATION, List.of("inflearn"), List.of()),
            service("UDEMY", "Udemy", EDUCATION, List.of("유데미"), List.of()),
            service("COURSERA", "Coursera", EDUCATION, List.of("코세라"), List.of()),
            // 클래스101은 2023-02 월간 구독 종료 → 연간 구독만 남았다.
            service("CLASS101", "클래스101", EDUCATION, List.of("class101"),
                    List.of(new Plan("연간 구독", 199000L, YEARLY))),
            service("TALING", "탈잉", EDUCATION, List.of("taling"), List.of()),
            service("YANADOO", "야나두", EDUCATION, List.of("yanadoo"), List.of()),
            service("RINGLE", "링글", EDUCATION, List.of("ringle"), List.of()),
            service("SPEAK", "스픽", EDUCATION, List.of("speak"),
                    List.of(new Plan("프리미엄", 129000L, YEARLY),
                            new Plan("프리미엄 플러스", 299000L, YEARLY))),

            service("XBOX_GAME_PASS", "Xbox Game Pass", GAME, List.of("엑스박스 게임패스", "game pass"),
                    List.of(new Plan("에센셜", 10800L, MONTHLY),
                            new Plan("프리미엄", 14900L, MONTHLY),
                            new Plan("PC Game Pass", 18000L, MONTHLY),
                            new Plan("얼티밋", 29000L, MONTHLY))),
            service("PS_PLUS", "PS Plus", GAME, List.of("playstation plus", "플레이스테이션 플러스"),
                    List.of(new Plan("에센셜", 12000L, MONTHLY),
                            new Plan("스페셜", 16200L, MONTHLY),
                            new Plan("디럭스", 19000L, MONTHLY))),
            service("NINTENDO_SWITCH_ONLINE", "Nintendo Switch Online", GAME, List.of("닌텐도 스위치 온라인"),
                    List.of(new Plan("개인 플랜", 24900L, YEARLY),
                            new Plan("패밀리 플랜", 47900L, YEARLY))),
            service("EA_PLAY", "EA Play", GAME, List.of("ea play"),
                    List.of(new Plan("EA Play", 7000L, MONTHLY),
                            new Plan("EA Play (연간)", 54000L, YEARLY),
                            new Plan("EA Play Pro", 22350L, MONTHLY),
                            new Plan("EA Play Pro (연간)", 133000L, YEARLY))),

            service("COUPANG_WOW", "쿠팡 와우", SHOPPING, List.of("coupang wow", "쿠팡와우"),
                    List.of(new Plan("와우 멤버십", 7890L, MONTHLY))),
            // 쿠팡이츠는 독립적으로 결제하는 구독 상품이 아니다(무료배달은 와우 멤버십 혜택).
            // 요금제를 주면 와우와 별개 지출로 이중 등록되므로 신규 등록 선택지에서 뺀다.
            // 다만 영수증에 "쿠팡이츠" 표기가 들어오므로 OCR 매칭 대상으로는 남긴다 —
            // 최장 매칭 덕분에 와우/플레이/이츠가 서로 뭉개지지 않는다.
            ocrOnly("COUPANG_EATS", "쿠팡이츠", SHOPPING, List.of("coupang eats", "쿠팡 이츠")),
            service("NAVER_PLUS", "네이버플러스", SHOPPING, List.of("naver plus", "네이버 플러스"),
                    List.of(new Plan("멤버십", 4900L, MONTHLY))),
            // 신세계 유니버스 클럽은 2026-01-01 신규 가입·연장 종료.
            ocrOnly("SSG_UNIVERSE_CLUB", "SSG.COM 유니버스클럽", SHOPPING, List.of("ssg 유니버스클럽")),
            service("BAEMIN_CLUB", "배민클럽", SHOPPING, List.of("우아한형제들", "배달의민족", "baemin club"),
                    List.of(new Plan("배민클럽", 3990L, MONTHLY))),
            service("YOGIPASS", "요기패스", SHOPPING, List.of("yogiyo", "요기요"),
                    List.of(new Plan("요기패스X", 2900L, MONTHLY))),

            service("FIGMA", "Figma", DESIGN, List.of("피그마"),
                    List.of(new Plan("Professional", 22000L, MONTHLY))),
            service("ADOBE_CC", "Adobe CC", DESIGN, List.of("adobe creative cloud", "어도비"),
                    List.of(new Plan("포토그래피 플랜", 26400L, MONTHLY),
                            new Plan("모든 앱", 70100L, MONTHLY))),
            service("CANVA", "Canva", DESIGN, List.of("캔바"), List.of())
    );

    /**
     * 이 길이 이하이면서 ASCII 로만 이루어진 alias(FLO, NYT, EA Play 의 "ea" 등)는 무관한 영문 단어의
     * 일부와 우연히 겹치기 쉬워, 단어 경계까지 확인한 뒤에만 매칭으로 인정한다.
     * 한글 짧은 이름(멜론, 티빙, 왓챠 …)은 조사·복합어가 붙어 띄어쓰기 없이 이어지는 일이 흔해
     * 단어 경계를 요구하면 오히려 정상 매칭이 깨지므로 이 규칙에서 제외한다.
     */
    private static final int SHORT_ASCII_ALIAS_MAX_LENGTH = 3;

    private ServiceCatalog() {
    }

    public static List<CatalogService> services() {
        return SERVICES;
    }

    public static Optional<CatalogService> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return SERVICES.stream().filter(service -> service.code().equals(code)).findFirst();
    }

    /**
     * OCR 텍스트에서 알려진 서비스를 찾는다. 완전 일치가 아니라 "등록된 이름이 텍스트 안에 들어있는지"를 보며,
     * 여러 서비스가 걸리면 <b>매칭된 이름이 가장 긴</b> 서비스를 고른다.
     * 예를 들어 "쿠팡이츠"는 "쿠팡 와우"보다 "쿠팡이츠"에 더 길게 걸리므로 계열 서비스끼리 섞이지 않는다.
     */
    public static Optional<CatalogService> match(String text) {
        String raw = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String normalized = normalize(raw);
        return SERVICES.stream()
                .map(service -> new Matched(service, matchedLength(service, raw, normalized)))
                .filter(matched -> matched.length() > 0)
                .max(Comparator.comparingInt(Matched::length))
                .map(Matched::service);
    }

    private record Matched(CatalogService service, int length) {
    }

    /** 매칭에 성공한 이름 중 가장 긴 것의 길이. 하나도 매칭되지 않으면 0. */
    private static int matchedLength(CatalogService service, String rawText, String normalizedText) {
        int longest = 0;
        for (String candidate : candidates(service)) {
            if (matches(candidate, rawText, normalizedText)) {
                longest = Math.max(longest, normalize(candidate).length());
            }
        }
        return longest;
    }

    private static List<String> candidates(CatalogService service) {
        return Stream.concat(Stream.of(service.canonicalName()), service.aliases().stream()).toList();
    }

    private static boolean matches(String candidate, String rawText, String normalizedText) {
        String normalizedCandidate = normalize(candidate);
        if (normalizedCandidate.isEmpty()) {
            return false;
        }
        if (isShortAscii(normalizedCandidate)) {
            return containsAsWord(rawText, candidate.toLowerCase(Locale.ROOT));
        }
        return normalizedText.contains(normalizedCandidate);
    }

    private static boolean isShortAscii(String normalizedCandidate) {
        return normalizedCandidate.length() <= SHORT_ASCII_ALIAS_MAX_LENGTH
                && normalizedCandidate.chars().allMatch(c -> c < 128);
    }

    private static boolean containsAsWord(String rawText, String candidate) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(candidate) + "(?![\\p{L}\\p{N}])")
                .matcher(rawText)
                .find();
    }

    // 괄호/언더스코어 등 구두점이 섞인 영수증 표기("쿠팡(와우 멤버십)")도 alias와 매칭되도록 문자/숫자만 남긴다.
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
