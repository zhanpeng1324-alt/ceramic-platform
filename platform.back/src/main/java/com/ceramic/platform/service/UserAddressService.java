package com.ceramic.platform.service;

import com.ceramic.platform.entity.UserAddress;
import com.ceramic.platform.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAddressService {
    
    private final UserAddressMapper userAddressMapper;
    
    public UserAddressService(UserAddressMapper userAddressMapper) {
        this.userAddressMapper = userAddressMapper;
    }
    
    @Transactional
    public UserAddress addAddress(UserAddress address) {
        if (address.getIsDefault() == null) {
            address.setIsDefault(false);
        }
        
        if (address.getIsDefault()) {
            userAddressMapper.clearDefault(address.getUserId());
        } else {
            UserAddress existingDefault = userAddressMapper.findDefaultByUserId(address.getUserId());
            if (existingDefault == null) {
                address.setIsDefault(true);
            }
        }
        
        userAddressMapper.insert(address);
        return address;
    }
    
    public List<UserAddress> getAddresses(Long userId) {
        return userAddressMapper.findByUserId(userId);
    }
    
    public UserAddress getAddress(Long id) {
        return userAddressMapper.findById(id);
    }
    
    @Transactional
    public UserAddress updateAddress(UserAddress address) {
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            userAddressMapper.clearDefault(address.getUserId());
        }
        userAddressMapper.update(address);
        return userAddressMapper.findById(address.getId());
    }
    
    @Transactional
    public void deleteAddress(Long id) {
        UserAddress address = userAddressMapper.findById(id);
        if (address != null && address.getIsDefault()) {
            userAddressMapper.deleteById(id);
            List<UserAddress> remaining = userAddressMapper.findByUserId(address.getUserId());
            if (!remaining.isEmpty()) {
                remaining.get(0).setIsDefault(true);
                userAddressMapper.update(remaining.get(0));
            }
        } else {
            userAddressMapper.deleteById(id);
        }
    }
    
    @Transactional
    public UserAddress setDefault(Long userId, Long addressId) {
        userAddressMapper.clearDefault(userId);
        UserAddress address = userAddressMapper.findById(addressId);
        if (address != null) {
            address.setIsDefault(true);
            userAddressMapper.update(address);
        }
        return address;
    }
    
    public UserAddress getDefaultAddress(Long userId) {
        return userAddressMapper.findDefaultByUserId(userId);
    }
}
