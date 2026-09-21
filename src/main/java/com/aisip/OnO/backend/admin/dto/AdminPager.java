package com.aisip.OnO.backend.admin.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 목록 화면의 페이지 이동 정보.
 *
 * <p>링크는 지금 요청의 쿼리스트링을 그대로 두고 페이지 번호만 바꿔서 만든다.
 * 그래서 날짜나 유저 필터가 걸린 목록에서 다음 페이지로 넘어가도 필터가 풀리지 않는다.
 * 화면에 보이는 번호는 1부터, 쿼리 파라미터는 0부터다.
 */
public record AdminPager(
        int page,
        int size,
        int totalPages,
        long totalElements,
        long startItem,
        long endItem,
        String previousUrl,
        String nextUrl,
        List<Link> links
) {

    private static final int BLOCK_SIZE = 10;

    public record Link(int number, String url, boolean current) {
    }

    public static AdminPager of(
            HttpServletRequest request,
            String pageParam,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        long startItem = totalElements == 0 || page >= totalPages ? 0 : (long) page * size + 1;
        long endItem = startItem == 0 ? 0 : Math.min((long) (page + 1) * size, totalElements);

        int blockStart = (page / BLOCK_SIZE) * BLOCK_SIZE;
        int blockEnd = Math.min(blockStart + BLOCK_SIZE - 1, Math.max(totalPages - 1, 0));

        List<Link> links = new ArrayList<>();
        for (int i = blockStart; i <= blockEnd && i < totalPages; i++) {
            links.add(new Link(i + 1, urlFor(request, pageParam, i), i == page));
        }

        String previousUrl = page > 0 && totalPages > 0
                ? urlFor(request, pageParam, Math.min(page - 1, totalPages - 1))
                : null;
        String nextUrl = page < totalPages - 1 ? urlFor(request, pageParam, page + 1) : null;

        return new AdminPager(page, size, totalPages, totalElements, startItem, endItem, previousUrl, nextUrl, links);
    }

    private static String urlFor(HttpServletRequest request, String pageParam, int page) {
        String query = request.getQueryString();
        return UriComponentsBuilder
                .fromUriString(request.getRequestURI() + (query == null ? "" : "?" + query))
                .replaceQueryParam(pageParam, page)
                .build()
                .toUriString();
    }
}
