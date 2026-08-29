package com.bank.money_transfer.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER_NAME);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, requestId);          // เก็บไว้ให้ที่อื่นในระบบเรียกใช้ได้
        response.setHeader(HEADER_NAME, requestId);  // ใส่กลับไปใน response header เสมอ

        try {
            filterChain.doFilter(request, response);  // ปล่อยให้ request ไหลต่อไป Controller ตามปกติ
        } finally {
            MDC.remove(MDC_KEY);  // ล้างทิ้งกันหลุดไปปนกับ request ถัดไป (thread ถูก reuse)
        }
    }
}
