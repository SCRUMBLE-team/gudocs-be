package com.scrumble.gudocs.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Firebase Admin 초기화. app.firebase.enabled=true 일 때만 동작한다.
 * 크레덴셜은 GOOGLE_APPLICATION_CREDENTIALS(서비스 계정 JSON 경로) 환경변수로 주입한다.
 * local/test 환경에서는 이 설정이 로드되지 않아 크레덴셜 없이도 애플리케이션이 정상 기동한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
