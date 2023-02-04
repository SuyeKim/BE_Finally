package com.sparta.be_finally.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequestDto {

    @Size(min = 5, max = 10)
    @NotBlank(message = "아이디는 필수 입력 값입니다.")
    @Pattern(regexp="(?=.*[0-9])(?=.*[a-z])(?=\\S+$).{5,10}", message = "아이디는 5~10자 영문 소문자, 숫자를 사용하세요.")
    private String userId;

    @Size(min = 8, max = 15)
    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Pattern(regexp = "(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\W)(?=\\S+$).{8,15}", message = "비밀번호는 8~15자 영문 대 소문자, 숫자, 특수문자를 사용하세요.")
    private String password;
    
    @NotBlank
    private String nickname;

    @NotBlank(message = "휴대폰번호는 필수 입력 값입니다.")
    @Pattern(regexp = "^01(?:0|1|[6-9])(?:\\d{4})\\d{4}$", message = "올바른 휴대폰번호 형식으로 입력해주세요.")
    private String phoneNumber;

}
