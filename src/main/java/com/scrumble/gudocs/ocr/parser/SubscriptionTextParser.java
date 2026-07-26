package com.scrumble.gudocs.ocr.parser;

import com.scrumble.gudocs.ocr.dto.response.OcrSubscriptionResult;
import com.scrumble.gudocs.subscriptions.entity.BillingCycle;
import com.scrumble.gudocs.subscriptions.entity.PaymentMethod;
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

        var matched = KnownServiceRegistry.match(text);
        String serviceName = matched
                .map(service -> service.canonicalName())
                .orElseGet(() -> guessServiceName(text));
        SubscriptionCategory category = matched.map(service -> service.category()).orElse(null);

        Long price = parsePrice(text);
        LocalDate firstBillingDate = parseDate(text, today);
        BillingCycle billingCycle = parseBillingCycle(text);
        PaymentMethod paymentMethod = parsePaymentMethod(text);

        return new OcrSubscriptionResult(serviceName, category, price, billingCycle, firstBillingDate, paymentMethod);
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

    private static PaymentMethod parsePaymentMethod(String text) {
        if (text.contains("간편결제")) return PaymentMethod.SIMPLE_PAY;
        if (text.contains("계좌이체")) return PaymentMethod.BANK_TRANSFER;
        if (text.contains("카드") || text.contains("일시불") || text.contains("할부")) return PaymentMethod.CARD;
        return null;
    }
}
