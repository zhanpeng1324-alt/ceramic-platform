package com.ceramic.platform.service;

import com.ceramic.platform.common.JwtUtil;
import com.ceramic.platform.common.UserCache;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.mapper.ChatMapper;
import com.ceramic.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserCache userCache;
    // 直接注入 ChatMapper 做级联删除。不能注入 ChatService：
    // ChatService → RealtimePushService → WebSocket 鉴权拦截器 → UserService 会形成循环依赖
    private final ChatMapper chatMapper;

    public List<User> listAllUsers() {
        return userMapper.findAll();
    }

    public List<User> listCustomers() {
        return userMapper.findAll().stream()
                .filter(u -> "customer".equals(u.getRole()))
                .collect(Collectors.toList());
    }

    public List<User> listServiceUsers() {
        return userMapper.findAll().stream()
                .filter(u -> "service".equals(u.getRole()))
                .collect(Collectors.toList());
    }

    public Map<String, Object> registerByPhone(String phone, String password) {
        if (userMapper.findByPhone(phone) != null) {
            throw new IllegalArgumentException("该手机号已注册");
        }
        User user = new User();
        user.setUsername(phone);
        // 只用验证码注册：密码为随机不可用值，登录走验证码通道（与 loginOrRegisterBySms 一致）
        user.setPassword(passwordEncoder.encode(
                password != null && !password.isBlank() ? password : java.util.UUID.randomUUID().toString()));
        user.setPhone(phone);
        user.setNickname("用户" + phone.substring(phone.length() - 4));
        user.setRole("customer");
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        return result;
    }

    public Map<String, Object> loginByPhone(String phone, String password) {
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            throw new IllegalArgumentException("手机号或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("手机号或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        return result;
    }

    /**
     * 短信验证码登录：手机号已注册直接登录；未注册则自动创建账号（密码为随机不可用值，仅可经验证码通道使用）。
     */
    public Map<String, Object> loginOrRegisterBySms(String phone) {
        boolean isNew = false;
        User user = userMapper.findByPhone(phone);
        if (user == null) {
            isNew = true;
            user = new User();
            user.setUsername(phone);
            user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setRole("customer");
            userMapper.insert(user);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        result.put("newUser", isNew);
        return result;
    }

    public Map<String, Object> login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        return result;
    }

    public Map<String, Object> loginService(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"service".equals(user.getRole()) && !"admin".equals(user.getRole())) {
            throw new IllegalArgumentException("无权访问后台系统");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole(), user.getUsername());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("user", sanitize(user));
        return result;
    }

    public User register(String username, String password, String nickname, String email, String phone) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (email != null && userMapper.findByEmail(email) != null) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole("customer");
        userMapper.insert(user);
        return user;
    }

    public User registerService(String username, String password, String nickname, String email) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (email != null && userMapper.findByEmail(email) != null) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole("service");
        userMapper.insert(user);
        return user;
    }

    public User registerAdmin(String username, String password, String nickname, String email) {
        if (userMapper.findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (email != null && userMapper.findByEmail(email) != null) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setRole("admin");
        userMapper.insert(user);
        return user;
    }

    public User getById(Long id) {
        return userMapper.findById(id);
    }

    public void updateUser(User user) {
        userMapper.update(user);
        if (user != null) userCache.evict(user.getId());
    }

    public User updateProfile(Long userId, User changes) {
        User existing = getById(userId);
        if (existing == null) throw new IllegalArgumentException("用户不存在");
        if (changes.getNickname() != null) existing.setNickname(changes.getNickname().trim());
        if (changes.getEmail() != null) existing.setEmail(changes.getEmail().trim());
        if (changes.getPhone() != null) existing.setPhone(changes.getPhone().trim());
        if (changes.getAddress() != null) existing.setAddress(changes.getAddress().trim());
        if (changes.getAvatar() != null) existing.setAvatar(changes.getAvatar());
        userMapper.update(existing);
        userCache.evict(userId);
        return sanitize(getById(userId));
    }

    public User updateRole(Long userId, String role) {
        if (!"customer".equals(role) && !"service".equals(role) && !"admin".equals(role)) throw new IllegalArgumentException("无效的角色");
        User user = getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        user.setRole(role);
        userMapper.update(user);
        userCache.evict(userId);
        return sanitize(getById(userId));
    }

    @Transactional
    public void deleteUser(Long id) {
        userMapper.delete(id);
        userCache.evict(id);
        // 级联清理该顾客的会话与聊天记录，避免客服收件箱残留僵尸会话
        chatMapper.deleteMessagesByCustomerId(id);
        chatMapper.deleteConversationsByCustomerId(id);
    }

    public boolean isAdmin(Long userId) {
        User user = getById(userId);
        return user != null && "admin".equals(user.getRole());
    }

    public boolean isService(Long userId) {
        User user = getById(userId);
        return user != null && ("service".equals(user.getRole()) || "admin".equals(user.getRole()));
    }

    public boolean isCustomer(Long userId) {
        User user = getById(userId);
        return user != null && "customer".equals(user.getRole());
    }

    public User sanitize(User user) {
        if (user == null) return null;
        User copy = new User();
        copy.setId(user.getId());
        copy.setUsername(user.getUsername());
        copy.setNickname(user.getNickname());
        copy.setAvatar(user.getAvatar());
        copy.setRole(user.getRole());
        copy.setEmail(user.getEmail());
        copy.setPhone(user.getPhone());
        copy.setAddress(user.getAddress());
        copy.setCreatedAt(user.getCreatedAt());
        copy.setUpdatedAt(user.getUpdatedAt());
        return copy;
    }
}
