package org.userservice.userservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.userservice.userservice.clients.KafkaProducerClient;
import org.userservice.userservice.dtos.EmailDto;
import org.userservice.userservice.expections.UserAlreadyExistsException;
import org.userservice.userservice.expections.UserNotFoundException;
import org.userservice.userservice.expections.WrongPasswordEception;
import org.userservice.userservice.models.Session;
import org.userservice.userservice.models.SessionStatus;
import org.userservice.userservice.models.User;
import org.userservice.userservice.repositories.SessionRepository;
import org.userservice.userservice.repositories.UserRepository;

import javax.crypto.SecretKey;
import java.util.*;

@Service
public class AuthService {
    private UserRepository userRepository;
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    private SessionRepository sessionRepository;
    private SecretKey key = Jwts.SIG.HS256.key().build();
    private KafkaProducerClient kafkaProducerClient;

    @Autowired
    private ObjectMapper objectMapper;




    public AuthService(UserRepository userRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder,
                       SessionRepository sessionRepository,
                       KafkaProducerClient kafkaProducerClient) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.sessionRepository =sessionRepository;
        this.kafkaProducerClient = kafkaProducerClient;
    }

    public boolean signUp(String email, String password) throws UserAlreadyExistsException {

        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException(
                    "User with the email id : " + email + " alrady exist");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(bCryptPasswordEncoder.encode(password));

        userRepository.save(user);
        try {
            EmailDto emailDto = new EmailDto();
            emailDto.setTo(email);
            emailDto.setSubject("Welcome to Scaler");
            emailDto.setBody("Have a pleasant learning experience.");
            emailDto.setFrom("anuragbatch@gmail.com");

            kafkaProducerClient.sendMessage("user_signedin", objectMapper.writeValueAsString(emailDto));
        }catch (JsonProcessingException exception) {
            throw new RuntimeException(exception.getMessage());
        }


        return true;
    }

    public String login(String email, String password) throws UserNotFoundException, WrongPasswordEception {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            throw new UserNotFoundException(
                    "User with the email id : " + email + " alrady exist");
        }

        boolean matches = bCryptPasswordEncoder.matches(password, userOptional.get().getPassword());

        if (matches) {
            String token = createJwtToken(userOptional.get().getId(),
                    new ArrayList<>(),
                    userOptional.get().getEmail());

            Session session = new Session();
            session.setToken(token);
            session.setUser(userOptional.get());

            Calendar calendar = Calendar.getInstance();
            Date currentDate = calendar.getTime();

            calendar.add(Calendar.DAY_OF_MONTH, 30);
            Date datePlus30Days = calendar.getTime();
            session.setExpiringAt(datePlus30Days);

            sessionRepository.save(session);
            return token;
        } else {
            throw new WrongPasswordEception("Wrong Password");
        }
    }

    private String createJwtToken(Long userId, List<String> roles, String email) {
        Map<String, Object> dataInJwt = new HashMap<>();
        dataInJwt.put("user_id", userId);
        dataInJwt.put("roles", roles);
        dataInJwt.put("email", email);


        Calendar calendar = Calendar.getInstance();
        Date currentDate = calendar.getTime();

        calendar.add(Calendar.DAY_OF_MONTH, 30);
        Date datePlus30Days = calendar.getTime();

        String token = Jwts.builder()
                .claims(dataInJwt)
                .expiration(datePlus30Days)
                .issuedAt(new Date())
                .signWith(key)
                .compact();

        return token;
    }
    public boolean validate(String token) {
        try {
            Jws<Claims> claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            Date expiryAt = claims.getPayload().getExpiration();
            Long userId = claims.getPayload().get("user_id", Long.class);

        } catch (Exception e) {
            return false;
        }
        return true;
    }
}

