package com.sparta.be_finally.room.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.sparta.be_finally.common.dto.PrivateResponseBody;
import com.sparta.be_finally.common.errorcode.CommonStatusCode;
import com.sparta.be_finally.common.exception.RestApiException;
import com.sparta.be_finally.common.util.SecurityUtil;
import com.sparta.be_finally.common.validator.Validator;
import com.sparta.be_finally.photo.dto.FrameResponseDto;
import com.sparta.be_finally.photo.entity.Photo;
import com.sparta.be_finally.photo.repository.PhotoRepository;
import com.sparta.be_finally.room.dto.*;
import com.sparta.be_finally.room.entity.Room;
import com.sparta.be_finally.room.entity.RoomParticipant;
import com.sparta.be_finally.room.repository.RoomParticipantRepository;
import com.sparta.be_finally.room.repository.RoomRepository;
import com.sparta.be_finally.user.entity.User;
import com.sparta.be_finally.user.repository.UserRepository;
import io.openvidu.java.client.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.persistence.LockModeType;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomService {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final AmazonS3Client amazonS3Client;
    private final Validator validator;
    private OpenVidu openVidu;

    // OpenVidu 서버가 수신하는 URL
    @Value("${openvidu.url}")
    private String OPENVIDU_URL;

    // OpenVidu 서버와 공유되는 비밀
    @Value("${openvidu.secret}")
    private String OPENVIDU_SECRET;
    private final PhotoRepository photoRepository;

    // 어플리케이션 실행시 Bean 으로 등록
    // OpenVidu 객체를 활용해 spring은 OpenVidu 서버와 통신이 가능해짐
    @PostConstruct
    public OpenVidu openVidu() {
        return openVidu = new OpenVidu(OPENVIDU_URL, OPENVIDU_SECRET);
    }

    // 방 생성시 세션(Openvidu room) 초기화성
    @Transactional
    public RoomResponseDto createRoom(RoomRequestDto roomRequestDto) throws OpenViduJavaClientException, OpenViduHttpException {
        User user = SecurityUtil.getCurrentUser();

        // 방 이름 미입력시 에러 메세지 응답
        if (roomRequestDto.getRoomName().isEmpty()) {
            throw new RestApiException(CommonStatusCode.CREATE_ROOM_NAME);

        } else {
            // 사용자가 연결할 때 다른 사용자에게 전달할 선택적 데이터, 유저의 닉네임을 전달할 것
            String serverData = user.getNickname();

            // serverData 및 역할을 사용하여 connectionProperties 객체를 빌드합니다.
            ConnectionProperties connectionProperties = new ConnectionProperties.Builder()
                    .type(ConnectionType.WEBRTC)
                    .data(serverData)
                    .build();

            // 새로운 openvidu 세션 생성
            Session session = openVidu.createSession();

            // 생성된 세션과 해당 세션에 연결된 다른 peer 에게 보여줄 data 를 담은 token을 생성
            String token = session.createConnection(connectionProperties).getToken();

            // 방 생성
            Room room = roomRepository.save(new Room(roomRequestDto, user, session.getSessionId()));
            photoRepository.save(new Photo(room));

            // 방장 token update (토큰이 있어야 방에 입장 가능)
            userRepository.update(user.getId(), token);

            // 방장 방 입장 처리
            roomParticipantRepository.save(RoomParticipant.createRoomParticipant(room, user, "leader"));

            return RoomResponseDto.builder()
                    .id(room.getId())
                    .role("leader")
                    .roomName(roomRequestDto.getRoomName())
                    .nickname(user.getNickname())
                    .roomCode(room.getRoomCode())
                    .userCount(room.getUserCount())
                    .sessionId(session.getSessionId())
                    .token(token)
                    .expireDate(room.getExpireDate())
                    .build();
        }
    }

    // 방 입장하기
    @Transactional
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    public PrivateResponseBody roomEnter(RoomRequestDto.RoomCodeRequestDto roomCodeRequestDto) throws OpenViduJavaClientException, OpenViduHttpException {
        User user = SecurityUtil.getCurrentUser();
        String role = "leader";

        // 방 코드로 방 조회
        Room room = validator.existsRoom(roomCodeRequestDto.getRoomCode());

        // Openvidu room 입장 전, Openvidu sessionId 존재 여부 확인
        Session session = getSession(room.getSessionId());

        // room 테이블의 sessionId에 openvidu SessionId 이 저장 되어있는지 확인
        validator.existsRoomSessionId(session.getSessionId());

        // 사진 촬영 중이거나 촬영을 마친 방은 입장 불가
        Photo photo = photoRepository.findByRoom(room);

        if (photo.getPhotoOne() != null) {
            throw new RestApiException(CommonStatusCode.NOT_ALLOWED_TO_ENTER);
        }

        // 방 나간 후 재입장 처리(방장이 재 입장인경우)
        if (roomParticipantRepository.findRoomParticipantByUserIdAndRoomAndRole(user.getUserId(), room, role) != null) {

            // 세션(방)에 입장 할 수 있는 토큰 생성 후 입장한 user 에게 토큰 저장
            String token = validator.getToken(session, user);

            return new PrivateResponseBody(CommonStatusCode.REENTRANCE_ROOM,
                    RoomResponseDto.builder()
                            .id(room.getId())
                            .role(role)
                            .roomName(room.getRoomName())
                            .nickname(user.getNickname())
                            .roomCode(room.getRoomCode())
                            .userCount(room.getUserCount())
                            .sessionId(room.getSessionId())
                            .token(token)
                            .expireDate(room.getExpireDate())
                            .build());

        } //user가 재입장인 경우
        else if (roomParticipantRepository.findRoomParticipantByUserIdAndRoomAndRole(user.getUserId(), room, "user") != null) {
            String token = validator.getToken(session, user);

            return new PrivateResponseBody(CommonStatusCode.REENTRANCE_ROOM,
                    RoomResponseDto.builder()
                            .id(room.getId())
                            .role("user")
                            .roomName(room.getRoomName())
                            .nickname(user.getNickname())
                            .roomCode(room.getRoomCode())
                            .userCount(room.getUserCount())
                            .sessionId(room.getSessionId())
                            .token(token)
                            .expireDate(room.getExpireDate())
                            .build());

        } else if (roomParticipantRepository.findRoomParticipantByUserIdAndRoom(user.getUserId(), room) == null && room.getUserCount() < 4) {
            // 방 첫 입장

            // 세션(방)에 입장 할 수 있는 토큰 생성 후 입장한 user 에게 토큰 저장
            String token = validator.getToken(session, user);

            // 방 입장 인원수 +1 업데이트
            room.enter();

            // 참여자 DB에 USER 저장
            roomParticipantRepository.save(RoomParticipant.createRoomParticipant(room, user, "user"));

            return new PrivateResponseBody(CommonStatusCode.ENTRANCE_ROOM,
                    RoomResponseDto.builder()
                            .id(room.getId())
                            .role("user")
                            .roomName(room.getRoomName())
                            .nickname(user.getNickname())
                            .roomCode(room.getRoomCode())
                            .userCount(room.getUserCount())
                            .sessionId(room.getSessionId())
                            .token(token)
                            .expireDate(room.getExpireDate())
                            .build());

        } else {
            // 인원수 초과시 에러 메세지 응답
            throw new RestApiException(CommonStatusCode.FAIL_MAN_ENTER);
        }
    }

    @Transactional
    public void roomExit(Long roomId) throws OpenViduJavaClientException, OpenViduHttpException {
        User user = SecurityUtil.getCurrentUser();

        // roomId 로 방 조회
        Room room = validator.existsRoom(roomId);

        Session session = getSession(room.getSessionId());

        String getConnectionId = null;

        // Openvidu 에서 사용자 연결 끊기
        // Session.getActiveConnections()에서 반환된 목록에서 원하는 Connection 개체를 찾습니다
        List<Connection> activeConnections = session.getActiveConnections();

        for (Connection connection : activeConnections) {
            if (connection.getToken().equals(user.getToken())) {
                //getConnectionId = connection.getConnectionId();
                session.forceDisconnect(connection);

            }
        }

        // room_participant - roomId, userId 로 등록된 데이터 삭제 처리
        RoomParticipant roomParticipant = roomParticipantRepository.findByUserIdAndRoom(user.getUserId(),room);
        roomParticipantRepository.delete(roomParticipant);

        // 방 입장 인원수 -1 업데이트
        room.exit();

        // user.getToken = null 로 update
        userRepository.update(user.getId(), null);
    }

    @Transactional
    public void roomClose(RoomRequestDto.RoomCodeRequestDto roomCodeRequestDto) throws OpenViduJavaClientException, OpenViduHttpException {
        // 방 코드로 방 조회
        Room room = validator.existsRoom(roomCodeRequestDto.getRoomCode());

        Session session = getSession(room.getSessionId());

        // room_participant - roomId로 등록된 데이터 삭제 처리
        List<RoomParticipant> participants = roomParticipantRepository.findAllByRoomId(room.getId());

        for (RoomParticipant participant : participants) {
            roomParticipantRepository.delete(participant);
        }

        // Openvidu session 삭제 (방 종료)
        session.close();
    }

    private Session getSession(String sessionId) throws OpenViduJavaClientException, OpenViduHttpException {
        //오픈비두에 활성화된 세션을 모두 가져와 리스트에 담는다.
        //활성화된 session의 sessionId들을 room에서 get한 sessionId(입장할 채팅방의 sessionId)와 비교
        //같을 경우 해당 session으로 새로운 토큰을 생성한다.

        if (sessionId == null) {
            throw new RestApiException(CommonStatusCode.FAIL_ENTER_OPENVIDU);
        }

        openVidu.fetch();

        List<Session> activeSessionList = openVidu.getActiveSessions();

        Session session = null;

        for (Session getSession : activeSessionList) {
            if (getSession.getSessionId().equals(sessionId)) {
                session = getSession;
            }
        }

        if (session == null) {
            throw new RestApiException(CommonStatusCode.FAIL_ENTER_OPENVIDU);
        }

        return session;
    }

    @Transactional
    public PrivateResponseBody choiceFrame(Long roomId, FrameRequestDto frameRequestDto) {
        User user = SecurityUtil.getCurrentUser();

        if (!roomRepository.existsByIdAndUserId(roomId, user.getId())) {
            return new PrivateResponseBody(CommonStatusCode.FAIL_CHOICE_FRAME);
        }
        Room room = roomRepository.findById(roomId).orElse(null);

        int frameNum = frameRequestDto.getFrame();

        if (frameNum == 1) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/black.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 2) {
            String frameUrl =  String.valueOf(amazonS3Client.getUrl(bucket, "frame/mint.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 3) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/pink.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 4) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/purple.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 5) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/white.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 6) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/retro.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 7) {
            String frameUrl =String.valueOf (amazonS3Client.getUrl(bucket, "frame/sunset.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameRequestDto.getFrame() == 8) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/blackcloud.jpg"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));

        } else if (frameNum == 9) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/rainbow.jpg"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));


        } else if (frameNum == 10) {
            String frameUrl = String.valueOf(amazonS3Client.getUrl(bucket, "frame/whitecloud.png"));
            room.updateFrame(frameRequestDto,frameUrl);
            return new PrivateResponseBody<>(CommonStatusCode.CHOICE_FRAME, new FrameResponseDto(frameNum, frameUrl));
        }
        return new PrivateResponseBody(CommonStatusCode.FAIL_CHOICE_FRAME2);
    }


}



//        if (frameRequestDto.getFrame() == 1 ){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/black.png");
//            frameRepository.saveAndFlush(new Frame(1,frameUrl));
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(1,frameUrl));
//
//
//        }else if (frameRequestDto.getFrame()==2){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/mint.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(2, frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() ==3){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/pink.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(3,frameUrl));
//
//
//        } else if (frameRequestDto.getFrame() == 4){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/purple.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(4,frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() == 5){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/white.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(5,frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() == 6){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/retro.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(6,frameUrl));
//
//
//        } else if (frameRequestDto.getFrame() == 7){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/sunset.png");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(7,frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() == 8){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/blackcloud.jpg");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(8, frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() == 9){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/rainbow.jpg");
//            return new PrivateResponseBody(CommonStatusCode.CHOICE_FRAME, new Frame(9,frameUrl));
//
//
//        }else if (frameRequestDto.getFrame() ==10){
//            URL frameUrl = amazonS3Client.getUrl(bucket,"frame/whitecloud.png");
//            return new PrivateResponseBody<>(CommonStatusCode.CHOICE_FRAME, new Frame(10,frameUrl));
//
//        }
//        return new PrivateResponseBody(CommonStatusCode.FAIL_CHOICE_FRAME2);
//    }
//}


















