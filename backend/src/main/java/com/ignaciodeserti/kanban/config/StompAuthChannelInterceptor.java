package com.ignaciodeserti.kanban.config;

import com.ignaciodeserti.kanban.repository.BoardRepository;
import com.ignaciodeserti.kanban.repository.UserRepository;
import com.ignaciodeserti.kanban.security.JwtService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * WebSocket/STOMP traffic never passes through the regular JWT servlet filter (the handshake is a
 * plain, unauthenticated HTTP upgrade). Instead, the client sends its access token as a STOMP
 * "Authorization" header on CONNECT, which this interceptor validates and turns into the frame's
 * Principal — then re-checks board ownership on every SUBSCRIBE, so one user can't listen in on
 * another user's board.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern BOARD_TOPIC = Pattern.compile("^/topic/boards/(\\d+)$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final BoardRepository boardRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            requireBoardOwnership(accessor);
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessagingException("Missing bearer token");
        }

        String token = header.substring(7);
        String email = jwtService.extractEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (!jwtService.isTokenValid(token, userDetails.getUsername())) {
            throw new MessagingException("Invalid or expired token");
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
    }

    private void requireBoardOwnership(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = BOARD_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return; // not a board topic — nothing to authorize here
        }

        Authentication auth = (Authentication) accessor.getUser();
        if (auth == null) {
            throw new MessagingException("Not authenticated");
        }

        Long boardId = Long.valueOf(matcher.group(1));
        boolean owns =
                userRepository
                        .findByEmail(auth.getName())
                        .flatMap(user -> boardRepository.findByIdAndOwner(boardId, user))
                        .isPresent();

        if (!owns) {
            throw new MessagingException("Not your board");
        }
    }
}
