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
 *
 * <h2>해지 링크({@code cancelUrl})</h2>
 * 구독 상세 화면의 "해지하러 가기" 링크다. 요금과 마찬가지로 정적 상수이며 다음 규칙으로 채운다.
 * <ul>
 *     <li>공개적으로 안정된 <b>해지·구독관리 딥링크</b>가 있으면 그것을 쓴다
 *         (넷플릭스 {@code /cancelplan}, 애플 구독 관리, Google One 설정 등).</li>
 *     <li>국내 서비스 상당수는 로그인 후 마이페이지로 들어가는 구조라 딥링크가 공개돼 있지 않다.
 *         이때는 <b>깨지지 않는 상위 URL(서비스 홈)</b>을 쓴다. 추측한 경로를 넣으면 404로 이어져
 *         링크가 없는 것보다 나쁘다.</li>
 *     <li>해지할 결제가 없거나(무료 서비스) 실제 해지 대상이 다른 서비스면 {@code null}로 두고
 *         화면에서 링크를 감춘다.</li>
 * </ul>
 * 이 링크는 <b>웹 결제 기준</b>이다. App Store·Google Play 인앱결제로 가입했다면 각 스토어에서
 * 해지해야 하며 이 링크로는 해지되지 않는다 — 화면에 그 안내를 함께 노출한다.
 *
 * <h2>가격 변경 예고({@link PriceChange})</h2>
 * 공식 인상·인하 발표가 나오면 해당 {@link Plan}에 {@code withPriceChange(...)}로 예고를 붙인다.
 * 이 파일에 적혀 있다는 것이 곧 <b>사람이 공식 출처로 검증했다</b>는 뜻이며, 그래서 별도의
 * 검증 상태(DETECTED/VERIFIED)나 관리자 화면을 두지 않는다 — 검증 절차는 이 파일을 고치는 PR 리뷰다.
 * 붙여 두면 배치가 해당 요금제를 쓰는 사용자에게 알림을 보내고, 구독 상세·카탈로그 응답에도 함께 실린다.
 * <b>사용자의 실제 결제 금액은 자동으로 바뀌지 않는다</b>(기존가 유지·프로모션·제휴결합·인앱결제로
 * 사람마다 실제 청구액이 다르다) — 반영 여부는 사용자가 직접 고른다.
 */
public final class ServiceCatalog {

    /** 아래 요금을 공개 자료로 확인한 날짜. API 응답에 함께 내려 사용자가 기준 시점을 알 수 있게 한다. */
    public static final LocalDate PRICES_CHECKED_ON = LocalDate.of(2026, 8, 8);

    /**
     * 달러 청구 요금제를 원화로 환산할 때 쓰는 환율. 실시간 조회가 아니라 요금과 같은 주기로
     * 손으로 갱신한다 — 실제 청구액은 카드사 환율·해외결제 수수료가 얹혀 어차피 달라지므로,
     * 매일 갱신해도 정확해지지 않고 유지비만 늘어난다. 이 값으로 만든 요금제는
     * {@code Plan.approximate = true} 로 표시해 화면에서 추정치임을 밝힌다.
     */
    private static final double USD_TO_KRW = 1420;

    /**
     * 공식 발표된 가격 변경 예고. 유지보수자가 공식 출처를 확인하고 직접 적어 넣는다 —
     * <b>이 파일에 선언돼 있다는 것 자체가 "공식 출처로 검증됨"을 뜻한다.</b> 블로그·기사만 있는
     * 소문은 적지 않는다.
     *
     * <p><b>선언은 적용일이 지나도 바로 지우지 않는다.</b> 가격 변경 푸시는 적용일까지 공식 안내로
     * 한 번만 보내지만, 적용 후 첫 예정 결제일이 지난 뒤 사용자가 서비스에 다시 접속하면 프론트가
     * 결제 금액 확인 배너를 보여줘야 한다. 그 판단에 구가격과 변경 정보가 필요하므로 모든 구독이
     * 한 바퀴 돌 때까지(연간 결제까지 고려하면 13개월) 선언을 유지한다.
     *
     * <p>그래서 <b>구가격을 명시적으로 적는다.</b> {@link Plan#price()}는 "지금의 공식가"라
     * 적용일에 새 가격으로 올라가고, 그 뒤에는 구가격을 알 방법이 없기 때문이다. 구가격은 알림 대상과
     * 적용 후 배너 대상의 보수적인 식별 키다. 유지보수자는 적용일에 {@code price}를 새 가격으로 바꾸고
     * 이 선언은 그대로 둔다 —
     * 적용 전에는 {@code price == oldPrice}, 적용 후에는 {@code price == newPrice}가 된다
     * (둘 중 하나와도 일치하지 않으면 {@code ServiceCatalogTest}가 잡는다).
     *
     * @param oldPrice    변경 전 금액. 이 금액을 쓰고 있는 사용자가 알림 대상이다
     * @param newPrice    변경 후 금액
     * @param effectiveOn 실제 적용 예정일
     * @param announcedOn 공식 발표일
     * @param sourceUrl   공식 출처 URL (공식 공지·요금제 페이지·고객센터 문서)
     */
    public record PriceChange(Long oldPrice, Long newPrice, LocalDate effectiveOn, LocalDate announcedOn,
                              String sourceUrl) {
    }

    /**
     * @param approximate 원화 정가가 아니라 달러 요금을 {@link #USD_TO_KRW}로 환산한 값인지.
     *                    true 면 실제 청구액이 결제 시점 환율·해외결제 수수료에 따라 달라진다.
     * @param change      공식 발표된 가격 변경 예고. 예고가 없으면 null(대부분의 요금제).
     */
    public record Plan(String name, Long price, BillingCycle billingCycle, boolean approximate,
                       PriceChange change) {

        /**
         * 이 요금제에 공식 가격 변경을 붙인다. 적용 전·후 모두 같은 선언을 쓰고, 적용일에는
         * {@code price} 숫자만 새 금액으로 바꾼다.
         * <pre>
         * 적용 전: won("프리미엄", 17000L, MONTHLY).withPriceChange(17000L, 19000L, ...)
         * 적용 후: won("프리미엄", 19000L, MONTHLY).withPriceChange(17000L, 19000L, ...)
         * </pre>
         */
        public Plan withPriceChange(long oldPrice, long newPrice, LocalDate effectiveOn, LocalDate announcedOn,
                                    String sourceUrl) {
            return new Plan(name, price, billingCycle, approximate,
                    new PriceChange(oldPrice, newPrice, effectiveOn, announcedOn, sourceUrl));
        }
    }

    /** 국내 원화 정가 요금제. */
    private static Plan won(String name, long price, BillingCycle cycle) {
        return new Plan(name, price, cycle, false, null);
    }

    /**
     * 달러로 청구하는 요금제. 환산값이라 100원 단위로 반올림하고 추정치로 표시한다.
     * 달러 원금이 변하지 않는 참값이지만, 지출 분석이 원화 단일 통화라 여기서 환산해 싣는다.
     */
    private static Plan usd(String name, double dollars, BillingCycle cycle) {
        long won = Math.round(dollars * USD_TO_KRW / 100.0) * 100L;
        return new Plan(name, won, cycle, true, null);
    }

    /**
     * @param code       프론트 로고 매칭 키. UPPER_SNAKE, 불변.
     * @param selectable 신규 구독 등록 대상으로 고를 수 있는지. false 면 등록 화면 선택지에서 빠지고
     *                   서버도 이 code 로 들어온 등록을 거부한다. 다만 과거 영수증을 계속 인식해야 하므로
     *                   OCR 매칭 대상으로는 남는다. (종료된 서비스, 독립 구독 상품이 아닌 서비스)
     * @param cancelUrl  해지를 시작할 수 있는 페이지. {@code null} 이면 화면에 해지 링크를 띄우지 않는다.
     *                   자세한 규칙은 {@link ServiceCatalog} 클래스 주석 참고.
     */
    public record CatalogService(String code, String canonicalName, SubscriptionCategory category,
                                 boolean selectable, String cancelUrl,
                                 List<String> aliases, List<Plan> plans) {

        /** OCR로 읽은 금액과 정확히 일치하는 요금제를 찾는다. 없으면 비어 있는 값. */
        public Optional<Plan> planByPrice(Long price) {
            if (price == null) {
                return Optional.empty();
            }
            return plans.stream().filter(plan -> plan.price().equals(price)).findFirst();
        }
    }

    private static CatalogService service(String code, String name, SubscriptionCategory category,
                                          String cancelUrl, List<String> aliases, List<Plan> plans) {
        return new CatalogService(code, name, category, true, cancelUrl, aliases, plans);
    }

    /**
     * OCR 인식 전용 항목. 신규 등록 대상이 아니므로 요금제를 두지 않는다.
     * 서비스가 종료됐거나, 살아 있어도 독립적으로 결제하는 구독 상품이 아닌 경우다.
     * 해지 링크도 두지 않는다 — 종료된 서비스는 해지할 것이 없고, 독립 상품이 아닌 서비스는
     * 실제 해지 대상이 다른 서비스(예: 쿠팡이츠 → 와우 멤버십)라 잘못된 안내가 된다.
     */
    private static CatalogService ocrOnly(String code, String name, SubscriptionCategory category,
                                          List<String> aliases) {
        return new CatalogService(code, name, category, false, null, aliases, List.of());
    }

    /** 애플 구독(iCloud+, 애플뮤직, 애플TV)은 모두 이 한 화면에서 관리·해지한다. */
    private static final String APPLE_SUBSCRIPTIONS = "https://apps.apple.com/account/subscriptions";

    /** Google One 저장용량과 Google AI 요금제(Gemini)는 같은 화면에서 해지한다. */
    private static final String GOOGLE_ONE_SETTINGS = "https://one.google.com/settings";

    /** MS 365·Xbox 등 마이크로소프트 구독 공통 관리 화면. */
    private static final String MICROSOFT_SERVICES = "https://account.microsoft.com/services";

    private static final List<CatalogService> SERVICES = List.of(
            service("YOUTUBE_PREMIUM", "유튜브 프리미엄", OTT, "https://www.youtube.com/paid_memberships",
                    List.of("youtube premium", "유튜브프리미엄"),
                    List.of(won("프리미엄 라이트", 8500L, MONTHLY),
                            won("프리미엄", 14900L, MONTHLY),
                            won("프리미엄 (iOS 결제)", 19500L, MONTHLY))),
            service("NETFLIX", "넷플릭스", OTT, "https://www.netflix.com/cancelplan", List.of("netflix"),
                    List.of(won("광고형 스탠다드", 7000L, MONTHLY),
                            won("스탠다드", 13500L, MONTHLY),
                            won("프리미엄", 17000L, MONTHLY))),
            service("DISNEY_PLUS", "디즈니플러스", OTT, "https://www.disneyplus.com/account/subscription",
                    List.of("disney+", "disney plus", "디지니플러스"),
                    List.of(won("스탠다드", 9900L, MONTHLY),
                            won("프리미엄", 13900L, MONTHLY))),
            service("TVING", "티빙", OTT, "https://www.tving.com/", List.of("tving"),
                    List.of(won("광고형 스탠다드", 5500L, MONTHLY),
                            won("베이직", 9500L, MONTHLY),
                            won("스탠다드", 13500L, MONTHLY),
                            won("프리미엄", 17000L, MONTHLY))),
            // 쿠팡플레이 기본 시청은 와우 멤버십에 포함된다 — 그 금액은 COUPANG_WOW 한 곳에만 둔다.
            // 여기 요금제는 와우와 별개로 결제하는 쿠팡플레이 자체 유료 상품이다.
            service("COUPANG_PLAY", "쿠팡플레이", OTT, "https://www.coupangplay.com/", List.of("coupang play"),
                    List.of(won("스포츠 패스 (와우회원)", 12400L, MONTHLY),
                            won("스포츠 패스 (일반회원)", 19300L, MONTHLY))),
            service("WATCHA", "왓챠", OTT, "https://watcha.com/", List.of("watcha"),
                    List.of(won("베이직", 7900L, MONTHLY),
                            won("프리미엄", 12900L, MONTHLY))),
            service("WAVVE", "웨이브", OTT, "https://www.wavve.com/", List.of("wavve"),
                    List.of(won("광고형 스탠다드", 5500L, MONTHLY),
                            won("베이직", 7900L, MONTHLY),
                            won("스탠다드", 10900L, MONTHLY),
                            won("프리미엄", 13900L, MONTHLY))),
            service("AMAZON_PRIME_VIDEO", "아마존프라임비디오", OTT,
                    "https://www.primevideo.com/settings/account", List.of("amazon prime video", "prime video"),
                    List.of(won("프라임 비디오", 5500L, MONTHLY))),
            service("APPLE_TV", "애플TV", OTT, APPLE_SUBSCRIPTIONS, List.of("apple tv", "appletv"),
                    List.of(won("월간 구독", 6500L, MONTHLY))),
            service("LAFTEL", "라프텔", OTT, "https://laftel.net/", List.of("laftel"),
                    List.of(won("베이직", 9900L, MONTHLY),
                            won("프리미엄", 14900L, MONTHLY))),

            service("FLO", "FLO", MUSIC, "https://www.music-flo.com/", List.of("플로"),
                    List.of(won("무제한 듣기", 7900L, MONTHLY))),
            service("YOUTUBE_MUSIC", "유튜브뮤직", MUSIC, "https://www.youtube.com/paid_memberships",
                    List.of("youtube music"),
                    List.of(won("뮤직 프리미엄", 11900L, MONTHLY))),
            service("SPOTIFY", "스포티파이", MUSIC, "https://www.spotify.com/account/subscription/",
                    List.of("spotify"),
                    List.of(won("베이직", 8690L, MONTHLY),
                            won("개인", 11990L, MONTHLY),
                            won("듀오", 17985L, MONTHLY),
                            won("학생", 6600L, MONTHLY))),
            service("MELON", "멜론", MUSIC, "https://www.melon.com/", List.of("melon"),
                    List.of(won("모바일 스트리밍", 7590L, MONTHLY),
                            won("스트리밍 클럽", 8690L, MONTHLY),
                            won("스트리밍 플러스", 11990L, MONTHLY))),
            service("APPLE_MUSIC", "애플뮤직", MUSIC, APPLE_SUBSCRIPTIONS, List.of("apple music"),
                    List.of(won("개인", 8900L, MONTHLY),
                            won("가족", 13500L, MONTHLY))),
            service("GENIE_MUSIC", "지니뮤직", MUSIC, "https://www.genie.co.kr/", List.of("genie music", "지니"),
                    List.of(won("스마트 음악감상", 8140L, MONTHLY),
                            won("음악감상 (PC+모바일)", 9240L, MONTHLY),
                            won("초고음질 무제한", 15400L, MONTHLY))),
            service("BUGS", "벅스", MUSIC, "https://music.bugs.co.kr/", List.of("bugs", "벅스뮤직"),
                    List.of(won("무제한 듣기", 8690L, MONTHLY),
                            won("듣기 + MP3 30곡", 12900L, MONTHLY))),

            service("ICLOUD", "iCloud", CLOUD, APPLE_SUBSCRIPTIONS, List.of("아이클라우드"),
                    List.of(won("50GB", 1100L, MONTHLY),
                            won("200GB", 4400L, MONTHLY),
                            won("2TB", 14000L, MONTHLY),
                            won("6TB", 44000L, MONTHLY))),
            // Google One 요금제(구글 드라이브 저장용량 = Google One 구독).
            service("GOOGLE_DRIVE", "Google Drive", CLOUD, GOOGLE_ONE_SETTINGS,
                    List.of("구글드라이브", "구글 드라이브", "google one", "구글 원"),
                    List.of(won("베이직 100GB", 2400L, MONTHLY),
                            won("AI Plus 2TB", 11900L, MONTHLY),
                            won("AI Pro 5TB", 29000L, MONTHLY))),
            service("DROPBOX", "Dropbox", CLOUD, "https://www.dropbox.com/account/plan", List.of("드롭박스"),
                    List.of(usd("Plus (2TB)", 9.99, MONTHLY),
                            usd("Plus (2TB, 연간)", 119.88, YEARLY))),
            service("NAVER_CLOUD", "네이버 클라우드", CLOUD, "https://mybox.naver.com/",
                    List.of("naver cloud", "마이박스", "mybox"),
                    List.of(won("80GB", 1650L, MONTHLY),
                            won("180GB", 3300L, MONTHLY),
                            won("330GB", 5500L, MONTHLY),
                            won("2TB", 11000L, MONTHLY))),
            // OneDrive 100GB 단독 구독은 현재 "Microsoft 365 Basic" 이라는 이름으로 판매된다.
            service("ONEDRIVE", "OneDrive", CLOUD, MICROSOFT_SERVICES, List.of("원드라이브"),
                    List.of(won("Microsoft 365 Basic (100GB)", 2900L, MONTHLY),
                            won("Microsoft 365 Basic (100GB, 연간)", 29900L, YEARLY))),

            // Notion·Slack·Figma 의 요금제 화면은 워크스페이스별 경로라 공통 딥링크가 없다 → 홈으로 보낸다.
            service("NOTION", "Notion", PRODUCTIVITY, "https://www.notion.so/", List.of("노션"),
                    List.of(won("플러스", 16800L, MONTHLY),
                            won("비즈니스", 36000L, MONTHLY))),
            service("MICROSOFT_365", "Microsoft 365", PRODUCTIVITY, MICROSOFT_SERVICES,
                    List.of("ms365", "office 365"),
                    List.of(won("Personal", 12500L, MONTHLY),
                            won("Personal (연간)", 125000L, YEARLY),
                            won("Family", 15500L, MONTHLY),
                            won("Family (연간)", 155000L, YEARLY))),
            service("SLACK", "Slack", PRODUCTIVITY, "https://slack.com/", List.of("슬랙"),
                    List.of(usd("Pro (1인)", 8.75, MONTHLY),
                            usd("Business+ (1인)", 18, MONTHLY))),
            service("GOOGLE_WORKSPACE", "Google Workspace", PRODUCTIVITY,
                    "https://admin.google.com/", List.of("구글 워크스페이스"),
                    List.of(usd("Business Starter (1인)", 7, MONTHLY),
                            usd("Business Standard (1인)", 12, MONTHLY))),

            service("CHATGPT", "ChatGPT", AI, "https://chatgpt.com/", List.of("chatgpt plus", "챗지피티", "openai"),
                    List.of(won("Go", 15000L, MONTHLY),
                            won("Plus", 29000L, MONTHLY),
                            won("Pro", 159000L, MONTHLY))),
            service("CLAUDE", "Claude", AI, "https://claude.ai/settings/billing", List.of("클로드", "anthropic"),
                    List.of(usd("Pro", 20, MONTHLY),
                            usd("Max 5x", 100, MONTHLY),
                            usd("Max 20x", 200, MONTHLY))),
            service("PERPLEXITY", "Perplexity", AI, "https://www.perplexity.ai/settings/account",
                    List.of("퍼플렉시티"),
                    List.of(usd("Pro", 20, MONTHLY),
                            usd("Max", 200, MONTHLY))),
            // Gemini 유료 요금제(Google AI Plus/Pro/Ultra)는 Google One 구독으로 청구된다.
            service("GEMINI", "Gemini", AI, GOOGLE_ONE_SETTINGS, List.of("제미나이"),
                    List.of(won("Google AI Plus", 11000L, MONTHLY),
                            won("Google AI Pro", 29000L, MONTHLY),
                            won("Google AI Ultra", 119000L, MONTHLY))),
            // 뤼튼은 개인 사용자 무료 정책이라 유료 요금제가 없다 — 해지할 결제도 없다.
            service("WRTN", "뤼튼", AI, null, List.of("wrtn"), List.of()),
            // 클로바X는 2026-04-09 개인 서비스 종료.
            ocrOnly("CLOVA_X", "클로바X", AI, List.of("clova x", "클로바엑스")),

            // NYT 는 4주마다 청구해(연 13회) MONTHLY/YEARLY 어느 쪽으로도 정확히 표현되지 않는다.
            // 잘못된 주기로 넣으면 지출 분석이 어긋나므로 비워 둔다.
            service("NYT", "NYT", NEWS, "https://myaccount.nytimes.com/", List.of("new york times", "뉴욕타임스"),
                    List.of()),
            service("MEDIUM", "Medium", NEWS, "https://medium.com/me/settings", List.of("미디엄"),
                    List.of(usd("멤버십", 5, MONTHLY),
                            usd("멤버십 (연간)", 50, YEARLY),
                            usd("Friend of Medium", 15, MONTHLY))),
            service("PUBLY", "퍼블리", NEWS, "https://publy.co/", List.of("publy"), List.of()),
            service("LONG_BLACK", "롱블랙", NEWS, "https://www.longblack.co/", List.of("long black"),
                    List.of(won("오늘의 노트", 5900L, MONTHLY),
                            won("무제한 노트", 9900L, MONTHLY))),
            service("OUTSTANDING", "아웃스탠딩", NEWS, "https://outstanding.kr/", List.of("outstanding"),
                    List.of(won("멤버십", 13900L, MONTHLY))),

            service("INFLEARN", "인프런", EDUCATION, "https://www.inflearn.com/", List.of("inflearn"), List.of()),
            service("UDEMY", "Udemy", EDUCATION, "https://www.udemy.com/", List.of("유데미"),
                    List.of(usd("Personal Plan", 35, MONTHLY),
                            usd("Personal Plan (연간)", 156, YEARLY))),
            service("COURSERA", "Coursera", EDUCATION, "https://www.coursera.org/", List.of("코세라"),
                    List.of(usd("Coursera Plus", 59, MONTHLY),
                            usd("Coursera Plus (연간)", 399, YEARLY))),
            // 클래스101은 2023-02 월간 구독 종료 → 연간 구독만 남았다.
            service("CLASS101", "클래스101", EDUCATION, "https://class101.net/", List.of("class101"),
                    List.of(won("연간 구독", 199000L, YEARLY))),
            service("TALING", "탈잉", EDUCATION, "https://taling.me/", List.of("taling"), List.of()),
            service("YANADOO", "야나두", EDUCATION, "https://www.yanadoo.co.kr/", List.of("yanadoo"), List.of()),
            service("RINGLE", "링글", EDUCATION, "https://www.ringleplus.com/", List.of("ringle"), List.of()),
            service("SPEAK", "스픽", EDUCATION, "https://www.speak.com/", List.of("speak"),
                    List.of(won("프리미엄", 129000L, YEARLY),
                            won("프리미엄 플러스", 299000L, YEARLY))),

            service("XBOX_GAME_PASS", "Xbox Game Pass", GAME, MICROSOFT_SERVICES,
                    List.of("엑스박스 게임패스", "game pass"),
                    List.of(won("에센셜", 10800L, MONTHLY),
                            won("프리미엄", 14900L, MONTHLY),
                            won("PC Game Pass", 18000L, MONTHLY),
                            won("얼티밋", 29000L, MONTHLY))),
            service("PS_PLUS", "PS Plus", GAME, "https://www.playstation.com/",
                    List.of("playstation plus", "플레이스테이션 플러스"),
                    List.of(won("에센셜", 12000L, MONTHLY),
                            won("스페셜", 16200L, MONTHLY),
                            won("디럭스", 19000L, MONTHLY))),
            service("NINTENDO_SWITCH_ONLINE", "Nintendo Switch Online", GAME, "https://accounts.nintendo.com/",
                    List.of("닌텐도 스위치 온라인"),
                    List.of(won("개인 플랜", 24900L, YEARLY),
                            won("패밀리 플랜", 47900L, YEARLY))),
            service("EA_PLAY", "EA Play", GAME, "https://www.ea.com/ea-play", List.of("ea play"),
                    List.of(won("EA Play", 7000L, MONTHLY),
                            won("EA Play (연간)", 54000L, YEARLY),
                            won("EA Play Pro", 22350L, MONTHLY),
                            won("EA Play Pro (연간)", 133000L, YEARLY))),

            service("COUPANG_WOW", "쿠팡 와우", SHOPPING, "https://www.coupang.com/",
                    List.of("coupang wow", "쿠팡와우"),
                    List.of(won("와우 멤버십", 7890L, MONTHLY))),
            // 쿠팡이츠는 독립적으로 결제하는 구독 상품이 아니다(무료배달은 와우 멤버십 혜택).
            // 요금제를 주면 와우와 별개 지출로 이중 등록되므로 신규 등록 선택지에서 뺀다.
            // 다만 영수증에 "쿠팡이츠" 표기가 들어오므로 OCR 매칭 대상으로는 남긴다 —
            // 최장 매칭 덕분에 와우/플레이/이츠가 서로 뭉개지지 않는다.
            ocrOnly("COUPANG_EATS", "쿠팡이츠", SHOPPING, List.of("coupang eats", "쿠팡 이츠")),
            service("NAVER_PLUS", "네이버플러스", SHOPPING, "https://nid.naver.com/membership/my",
                    List.of("naver plus", "네이버 플러스"),
                    List.of(won("멤버십", 4900L, MONTHLY))),
            // 신세계 유니버스 클럽은 2026-01-01 신규 가입·연장 종료.
            ocrOnly("SSG_UNIVERSE_CLUB", "SSG.COM 유니버스클럽", SHOPPING, List.of("ssg 유니버스클럽")),
            service("BAEMIN_CLUB", "배민클럽", SHOPPING, "https://www.baemin.com/",
                    List.of("우아한형제들", "배달의민족", "baemin club"),
                    List.of(won("배민클럽", 3990L, MONTHLY))),
            service("YOGIPASS", "요기패스", SHOPPING, "https://www.yogiyo.co.kr/", List.of("yogiyo", "요기요"),
                    List.of(won("요기패스X", 2900L, MONTHLY))),

            service("FIGMA", "Figma", DESIGN, "https://www.figma.com/", List.of("피그마"),
                    List.of(usd("Professional", 15, MONTHLY),
                            usd("Organization", 55, MONTHLY))),
            service("ADOBE_CC", "Adobe CC", DESIGN, "https://account.adobe.com/plans",
                    List.of("adobe creative cloud", "어도비"),
                    List.of(won("포토그래피 플랜", 26400L, MONTHLY),
                            won("모든 앱", 70100L, MONTHLY))),
            service("CANVA", "Canva", DESIGN, "https://www.canva.com/settings", List.of("캔바"),
                    List.of(usd("Pro", 15, MONTHLY),
                            usd("Pro (연간)", 120, YEARLY)))
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

    /** 저장된 구독의 service_code 로 해지 링크를 찾는다. 직접 입력한 서비스거나 링크가 없으면 null. */
    public static String cancelUrlOf(String code) {
        return findByCode(code).map(CatalogService::cancelUrl).orElse(null);
    }

    /**
     * 저장된 구독 1건에 해당하는 가격 변경 예고. 해지 링크와 마찬가지로 구독 행에 저장하지 않고
     * 매번 카탈로그에서 찾는다 — 예고를 고치거나 지우면 이미 등록된 구독도 즉시 따라간다.
     *
     * <p>구독에는 요금제명이 없으므로 <b>구가격·결제주기가 정확히 일치하는 요금제</b>를 그 사용자의
     * 요금제로 본다. 프로모션가·구요금제로 다른 금액을 넣어 둔 사용자는 애초에 이번 변경 대상이
     * 아니므로 자연히 제외되고, <b>이미 새 금액으로 반영한 사용자도 더 이상 일치하지 않아 빠진다</b>.
     * 이 정보는 적용 전 공식 변경 안내와 적용 후 서비스 내 확인 배너에 함께 쓰며, 적용 후 추가 푸시는
     * 발송하지 않는다.
     */
    public static Optional<PriceChange> priceChangeOf(String code, Long price, BillingCycle cycle) {
        if (price == null || cycle == null) {
            return Optional.empty();
        }
        return findByCode(code).stream()
                .flatMap(service -> service.plans().stream())
                .filter(plan -> plan.change() != null
                        && price.equals(plan.change().oldPrice())
                        && cycle == plan.billingCycle())
                .map(Plan::change)
                .findFirst();
    }

    /**
     * 가격 변경 예고가 선언된 요금제 전부. 발송 배치가 이 목록을 훑어 대상 사용자를 찾는다.
     * 평소에는 비어 있고, 인상 발표가 있는 동안에만 한두 건 들어 있다.
     */
    public static List<DeclaredPriceChange> declaredPriceChanges() {
        return SERVICES.stream()
                .flatMap(service -> service.plans().stream()
                        .filter(plan -> plan.change() != null)
                        .map(plan -> new DeclaredPriceChange(service, plan, plan.change())))
                .toList();
    }

    /** 어떤 서비스의 어떤 요금제에 붙은 변경 예고인지 함께 들고 다니기 위한 묶음. */
    public record DeclaredPriceChange(CatalogService service, Plan plan, PriceChange change) {
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
