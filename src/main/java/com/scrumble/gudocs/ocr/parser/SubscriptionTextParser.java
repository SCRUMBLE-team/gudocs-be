package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.subscriptions.catalog.ServiceCatalog;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.SubscriptionCategory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLOVA OCR 텍스트에서 구독 등록 필드를 best-effort로 추출한다.
 * 정규식 기반 휴리스틱이라 완벽하지 않다 — 실패한 필드는 null로 두고 사용자가 직접 채운다.
 */
public final class SubscriptionTextParser {

    private static final Pattern PRICE_PATTERN = Pattern.compile("([0-9][0-9,]*)\\s*원");
    private static final Pattern DATE_WITH_YEAR_PATTERN =
            Pattern.compile("(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern DATE_WITHOUT_YEAR_PATTERN =
            Pattern.compile("(\\d{1,2})\\s*[./월]\\s*(\\d{1,2})\\s*일?");
    private static final Pattern YEARLY_KEYWORD_PATTERN = Pattern.compile("연\\s*(결제|구독)|연간\\s*구독");

    private SubscriptionTextParser() {
    }

    public static OcrSubscriptionResult parse(String ocrText, LocalDate today) {
        String text = ocrText == null ? "" : ocrText;

        var matched = ServiceCatalog.match(text);
        String serviceName = matched
                .map(service -> service.canonicalName())
                .orElseGet(() -> guessServiceName(text));
        String serviceCode = matched.map(service -> service.code()).orElse(null);
        SubscriptionCategory category = matched.map(service -> service.category()).orElse(null);

        Long price = parsePrice(text);
        LocalDate firstBillingDate = parseDate(text, today);
        BillingCycle billingCycle = parseBillingCycle(text);

        // 영수증에서 읽은 금액이 항상 우선이다. 카탈로그 값으로 덮어쓰지 않고, 금액이 정확히 일치하는
        // 요금제가 있을 때만 이름을 덧붙인다 — 할인·프로모션·구 요금제 사용자가 있기 때문.
        String planName = matched
                .flatMap(service -> service.planByPrice(price))
                .map(plan -> plan.name())
                .orElse(null);

        return new OcrSubscriptionResult(
                serviceName, serviceCode, category, price, planName, billingCycle, firstBillingDate);
    }

    private static String guessServiceName(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static Long parsePrice(String text) {
        Matcher matcher = PRICE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String text, LocalDate today) {
        Matcher withYear = DATE_WITH_YEAR_PATTERN.matcher(text);
        if (withYear.find()) {
            return toLocalDate(Integer.parseInt(withYear.group(1)),
                    Integer.parseInt(withYear.group(2)), Integer.parseInt(withYear.group(3)));
        }
        Matcher withoutYear = DATE_WITHOUT_YEAR_PATTERN.matcher(text);
        if (withoutYear.find()) {
            return toLocalDate(today.getYear(),
                    Integer.parseInt(withoutYear.group(1)), Integer.parseInt(withoutYear.group(2)));
        }
        return null;
    }

    private static LocalDate toLocalDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static BillingCycle parseBillingCycle(String text) {
        return YEARLY_KEYWORD_PATTERN.matcher(text).find() ? BillingCycle.YEARLY : BillingCycle.MONTHLY;
    }
}
