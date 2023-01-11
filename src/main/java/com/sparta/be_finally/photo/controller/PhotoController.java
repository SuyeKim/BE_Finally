package com.sparta.be_finally.photo.controller;
;
import com.sparta.be_finally.config.jwt.JwtUtil;
import com.sparta.be_finally.user.service.KakaoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Api(tags = {"Photo API"})
@Slf4j
@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoController {
    private final KakaoService kakaoService;

    @ApiOperation(value = "카카오톡 메시지 보내기")
    @GetMapping("/kakaoMessage")
    public void kakaoMessage(@RequestParam String code, HttpServletResponse response) throws IOException {
        // code: 카카오 서버로부터 받은 인가 코드
        String createToken = kakaoService.kakaoLoginCheck(code, response);

        // Cookie 생성 및 직접 브라우저에 Set
        Cookie cookie = new Cookie(JwtUtil.AUTHORIZATION_HEADER, createToken.substring(7));
        cookie.setPath("/");
        response.addCookie(cookie);

        // 친구 목록 조회
        String getFriendList = kakaoService.requestFriendList(createToken,10);

    }

}
