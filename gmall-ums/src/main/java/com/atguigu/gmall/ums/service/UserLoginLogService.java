package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.ums.entity.UserLoginLogEntity;

import java.util.Map;

/**
 * 用户登陆记录表
 *
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2021-03-25 19:34:13
 */
public interface UserLoginLogService extends IService<UserLoginLogEntity> {

    PageResultVo queryPage(PageParamVo paramVo);
}

