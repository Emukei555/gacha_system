//package com.yourcompany.security;
//
//import com.yourcompany.model.user.User;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import java.util.Collection;
//import java.util.List;
//
//public class CustomUserDetails implements UserDetails {
//    private final User user;
//
//    public CustomUserDetails(User user) {
//        this.user = user;
//    }
//
//    public User getUser() { return user; } // ドメインのUserを取り出せるように
//
//    @Override
//    public Collection<? extends GrantedAuthority> getAuthorities() {
//        // Enumの Role名（CLERK）の前に "ROLE_" を付与して返す
//        // これにより .hasRole("CLERK") と合致するようになります
//        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
//    }
//
//    @Override
//    public String getPassword() { return user.getPasswordHash(); }
//    @Override
//    public String getUsername() { return user.getEmail(); }
//    @Override
//    public boolean isAccountNonExpired() { return true; }
//    @Override
//    public boolean isAccountNonLocked() { return true; }
//    @Override
//    public boolean isCredentialsNonExpired() { return true; }
//    @Override
//    public boolean isEnabled() { return true; }
//}
