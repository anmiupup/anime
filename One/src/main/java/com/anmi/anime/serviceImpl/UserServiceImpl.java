package com.anmi.anime.serviceImpl;

import com.anmi.anime.domain.authorize.repository.UserRepository;
import com.anmi.anime.domain.authorize_gz.model.UserGZ;
import com.anmi.anime.domain.authorize_gz.repository.UserGZRepository;
import com.anmi.anime.domain.authorize.model.User;
import com.anmi.anime.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by wangjue on 2017/8/22.
 */
@Service("userService")
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserGZRepository userGZRepository;

    @Override
    public List<UserGZ> getAll() {
        List<UserGZ> userList = userGZRepository.findAll();
        return userList;
    }

    @Override
    public Page<User> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    public User findByPkId(String pkId) {
        return userRepository.findByPkId(pkId);
    }

    @Override
    public List<User> findByName(String name) {
        return userRepository.findByName(name);
    }

    @Override
    public User findByUserNameAndPassWord(String userName, String passWord) {
        return userRepository.findByUsernameAndPassword(userName,passWord);
    }

    @Override
    public List<User> findByNameContaining(String name) {
        return userRepository.findByNameContaining(name);
    }

    @Override
    public List<User> findByState(Integer delTag) {
        return userRepository.findByState(delTag);
    }
}