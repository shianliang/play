package controllers.wechat.front;

import java.util.List;

import models.common.bean.CurrUser;
import models.common.entity.t_information;
import models.common.entity.t_send_code;
import models.common.entity.t_template_notice;
import models.common.entity.t_user;

import org.apache.commons.lang.StringUtils;

import play.cache.Cache;
import play.libs.Codec;
import play.mvc.Scope.Session;
import services.common.InformationService;
import services.common.NoticeService;
import services.common.SendCodeRecordsService;
import services.common.UserService;

import com.shove.Convert;
import com.shove.security.Encrypt;

import common.constants.CacheKey;
import common.constants.ConfConst;
import common.constants.Constants;
import common.constants.ModuleConst;
import common.constants.SettingKey;
import common.constants.SmsScene;
import common.constants.WxPageType;
import common.enums.Client;
import common.enums.NoticeScene;
import common.utils.Factory;
import common.utils.LoggerUtil;
import common.utils.ResultInfo;
import common.utils.SMSUtil;
import common.utils.StrUtil;
import common.utils.captcha.CaptchaUtil;
import controllers.wechat.WechatBaseController;
import controllers.wechat.front.account.MyAccountCtrl;

/**
 * 微信-登录注册控制器
 *
 * @description 
 *
 * @author DaiZhengmiao
 * @createDate 2016年5月4日
 */
public class LoginAndRegisteCtrl extends  WechatBaseController {

	protected static UserService userService = Factory.getService(UserService.class);
	
	protected static InformationService informationService = Factory.getService(InformationService.class);
	
	protected static NoticeService noticeService = Factory.getService(NoticeService.class);
	
	protected static SendCodeRecordsService sendCodeRecordsService = Factory.getService(SendCodeRecordsService.class);

	/**
	 * 注册页面
	 *
	 *@description 
	 *
	 * @author DaiZhengmiao
	 * @createDate 2016年5月9日
	 */
    public static void registerPre(){
    	/* 验证码 */
		String randomId = Codec.UUID();
		
		/* 禁止该页面进行高速缓存 */
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "0");
		
		/* 获取平台协议标题 */
		t_information platformRegister = informationService.findRegisterDeal();
		
		/* cps 邀请码 推广 */
		String recommendCode = Encrypt.decrypt3DES(params.get("recommendCode"), ConfConst.ENCRYPTION_KEY_DES);
    
		render(platformRegister, randomId,recommendCode);
    }
	
    /**
     * 用户注册协议
     *
     *
     * @author DaiZhengmiao
     * @createDate 2016年5月9日
     */
	public static void registerDealPre(){
		t_information registerDeal = informationService.findRegisterDeal();
		if (registerDeal == null) {

			toResultPage(WxPageType.PAGE_COMMUNAL_FAIL, "页面未找到");
		}
		
		render(registerDeal);
	}

	/**
	 * 发送短信验证码（注册）
	 *
	 * @param mobile 手机号码
	 *
	 * @author DaiZhengmiao
	 * @createDate 2016年5月9日
	 */
	public static void sendCodeOfRegister(String mobile, String randomId, String code) {
		String scene = SmsScene.WX_REGISTER;
		ResultInfo result = new ResultInfo();

		if (StringUtils.isBlank(mobile)) {
			result.code = -2;
			result.msg = "手机号不能为空";

			renderJSON(result);
		}
		if (userService.isMobileExists(mobile)) {
			result.code = -2;
			result.msg = "该手机号已被占用";

			renderJSON(result);
		}
	//TODO 改版
	/*	if (StringUtils.isBlank(code)) {
			result.code = -3;
			result.msg = "验证码不能为空";
			renderJSON(result);
		}*/
		
		/* 根据randomId取得对应的验证码值 */
		String RandCode = CaptchaUtil.getCode(randomId);

		/* 验证码失效 */
		if (RandCode == null) {
			result.code = -3;
			result.msg = "验证码已失效";
			renderJSON(result);
		}

		/* 验证码错误 */
		if (ConfConst.IS_CHECK_PIC_CODE) {
			if (!code.equalsIgnoreCase(RandCode)) {
				result.code = -3;
				result.msg = "验证码不正确";
				renderJSON(result);
			}
		}else{
			Cache.delete(randomId);
		}
		
		/* 根据手机号码查询短信发送条数 */
    	List<t_send_code> recordByMobile = sendCodeRecordsService.querySendRecordsByMobile(mobile);
		if (recordByMobile.size() >= ConfConst.SEND_SMS_MAX_MOBILE) {
			result.code = -4;
			result.msg = "该手机号码单日短信发送已达上限";

			renderJSON(result);
		}
    	
    	/* 根据IP查询短信发送条数 */
    	List<t_send_code> recordByIp = sendCodeRecordsService.querySendRecordsByIp(getIp());
		if (recordByIp.size() >= ConfConst.SEND_SMS_MAX_IP) {
			result.code = -4;
			result.msg = "该IP单日短信发送已达上限";

			renderJSON(result);
		}
		
		/* 将手机号码存入缓存，用于判断60S中内同一手机号不允许重复发送验证码 */
		String encryString = Session.current().getId();
    	Object cache = Cache.get(mobile + encryString + scene);
		if (null == cache) {
			Cache.set(mobile + encryString + scene, mobile,Constants.CACHE_TIME_SECOND_60);
		} else {
			String isOldMobile = cache.toString();
			if (isOldMobile.equals(mobile)) {
				result.code = -4;
				result.msg = "短信已发送";

				renderJSON(result);
			}
		}
    	
    	/* 查询短信验证码模板 */
		List<t_template_notice> noticeList = noticeService.queryTemplatesByScene(NoticeScene.SECRITY_CODE);

		if (noticeList.size() < 0) {
			result.code = -4;
			result.msg = "短信发送失败";

			renderJSON(result);
		}
		String content = noticeList.get(0).content;
		String smsAccount = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_ACCOUNT);
		String smsPassword = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_PASSWORD);

		/* 发送短信验证码 */
		SMSUtil.sendCode(smsAccount, smsPassword, mobile, content, Constants.CACHE_TIME_MINUS_30, scene,ConfConst.IS_CHECK_MSG_CODE);
		
		/* 添加一条短信发送控制记录 */
		sendCodeRecordsService.addSendCodeRecords(mobile, getIp());
		
		result.code = 1;
		result.msg = "短信验证码发送成功";
		
		renderJSON(result);
	}
    
	 /**
     * 用户注册
     * @description 
     * 
     * @param mobile 手机号码
     * @param password 密码
     * @param confirmPassword 确认密码
     * @param randomId 验证码密文
     * @param code 验证码
     * @param smsCode 短信验证码
     * @param recommendCode 邀请码
     * @param scene 场景
     * @param readandagree 协议勾选
     * 
     * @author DaiZhengmiao
     * @createDate 2016年5月9日
     */
	public static void registering(String mobile, String password, String smsCode, String recommendCode) {
		ResultInfo result = new ResultInfo();
		String scene = SmsScene.WX_REGISTER;
		/* 回显数据 */
		flash.put("mobile", mobile);
		flash.put("recommendCode", recommendCode);
		
		if (StringUtils.isBlank(mobile)) {
			flash.error("请填写手机号");
			
			registerPre();
		}
		
		/* 验证手机号是否符合规范 */
		if (!StrUtil.isMobileNum(mobile)) {
			flash.error("手机号不符合规范");
			
			registerPre();
		}
		
		/* 判断手机号码是否存在 */
		if (userService.isMobileExists(mobile)) {
			flash.error("手机号码已存在");

			registerPre();
		}
		
		
		/* 短信验证码验证 */
		if (StringUtils.isBlank(smsCode)) {
			flash.error("请输入短信验证码");
			
			registerPre();
		}
		Object obj = Cache.get(mobile + scene);
		if (obj == null) {
			flash.error("短信验证码已失效");
			
			registerPre();
		}
		String codeStr = obj.toString();
		
		/** 短信验证码 验证 */
		if(ConfConst.IS_CHECK_MSG_CODE){
			if (!codeStr.equals(smsCode)) {
				flash.error("短信验证码错误");
				
				registerPre();
			}else{
				Cache.delete(mobile + scene);
				/* 清除缓存中的验证码 */
				String encryString = Session.current().getId();
		    	Object cache = Cache.get(mobile + encryString + scene);
				if (cache != null) {
		    		Cache.delete(mobile + encryString + scene);
		    		Cache.delete(mobile + scene);
		    	}
			}
		}
		
		/* 验证密码是否符合规范 */
		if (!StrUtil.isValidPassword(password)) {
			flash.error("密码不符合规范");

			registerPre();
		}
		
		int flagOfRecommendCode = 0;
		/* 验证邀请码 */
		if(StringUtils.isNotBlank(recommendCode)){
			
			if (StrUtil.isMobileNum(recommendCode)) {// CPS邀请码是用户的手机号
				if(common.constants.ModuleConst.EXT_CPS){ //CPS模块是否存在
					/* 判断手机号码是否存在 */
					flagOfRecommendCode = userService.isMobileExists(recommendCode) ? 1:-1;
				}
			} else {
				if (common.constants.ModuleConst.EXT_WEALTHCIRCLE) {// 财富圈邀请码
					service.ext.wealthcircle.WealthCircleService wealthCircleService = Factory.getService(service.ext.wealthcircle.WealthCircleService.class);
					ResultInfo result2 = wealthCircleService.isWealthCircleCodeUseful(recommendCode);
					if (result2.code == 1) {
						flagOfRecommendCode = 2;
					}
				}
			}
			if (flagOfRecommendCode < 0) {
				flash.error("推广码不存在");

				registerPre();
			}
		}

		/* 自动生成用户名 */
		String userName = userService.userNameFactory(mobile);

		/* 密码加密 */
		password = Encrypt.MD5(password + ConfConst.ENCRYPTION_KEY_MD5);
		result =  userService.registering(userName, mobile, password, Client.WECHAT, getIp());
		if (result.code < 1) {
			flash.error(result.msg);
			LoggerUtil.info(true, result.msg);
			
			registerPre();
		}else{
			//ext_redpacket 红包数据生成 start
			/*if (ModuleConst.EXT_REDPACKET) {
				t_user user = (t_user) result.obj;
				services.ext.redpacket.RedpacketService redpacketService = Factory.getService(services.ext.redpacket.RedpacketService.class);
				ResultInfo result2 = redpacketService.creatRedpacket(user.id); // 此处不能使用result
				if (result2.code < 1) {
					LoggerUtil.info(true, "注册成功，生成红包相关数据时出错，数据回滚，%s",result2.msg);

					flash.error(result2.msg);
					registerPre();
				}
			}*/
			//end
			
			//experienceBid 体验金发放 start
			if (ModuleConst.EXT_EXPERIENCEBID) {
				t_user user = (t_user) result.obj;
				service.ext.experiencebid.ExperienceBidAccountService experienceBidAccountService = Factory.getService(service.ext.experiencebid.ExperienceBidAccountService.class);
				ResultInfo result3 = experienceBidAccountService.createExperienceAccount(user.id);  //此处不能使用result
				if (result3.code < 1) {
					LoggerUtil.info(true, "注册成功，发放体验金到账户时，%s", result3.msg);
					
					flash.error(result3.msg);
					registerPre();
				}
			}
			//end
			
			// cps账户开户
			if (ModuleConst.EXT_CPS) {
				t_user user = (t_user) result.obj;
				services.ext.cps.CpsService cpsService = Factory.getService(services.ext.cps.CpsService.class);
				
				ResultInfo result4 = null;
				if (flagOfRecommendCode == 1) {
					result4 = cpsService.createCps(recommendCode, user.id);
				} else {
					result4 = cpsService.createCps(null, user.id);
				}
						
				if (result4.code < 1) {
					LoggerUtil.info(true, "注册成功，生成cps推广相关数据时出错，数据回滚，%s", result4.msg);
					
					flash.error(result4.msg);
					registerPre();
				}
			}
			//end
			
			// 财富圈账号数据创建
			if (ModuleConst.EXT_WEALTHCIRCLE) {
				t_user user = (t_user) result.obj;
				service.ext.wealthcircle.WealthCircleService wealthCircleService = Factory.getService(service.ext.wealthcircle.WealthCircleService.class);
				ResultInfo result5 = null ; 
						
				if (flagOfRecommendCode == 2) {
					result5 = wealthCircleService.creatWealthCircle(recommendCode, user.id);
				} else {
					result5 = wealthCircleService.creatWealthCircle(null, user.id);
				}
				if (result5.code < 1) {
					LoggerUtil.info(true, "注册成功，生成财富圈推广相关数据时出错，数据回滚，%s", result5.msg);
					
					flash.error(result5.msg);
					registerPre();
				}
			}
			//end
			
			toResultPage(WxPageType.PAGE_SUCC_PAYMENT, "注册成功");
		}
	}
	
	/**
	 * 用户登入页面
	 *
	 *
	 * @author DaiZhengmiao
	 * @createDate 2016年5月9日
	 */
	public static void loginPre(){
		if (getCurrUser() != null) {// 已经登录，直接进入账户首页
			MyAccountCtrl.toAccountHomePre();
		}
		String encryString = Session.current().getId();
		Integer  loginCount= (Integer)Cache.get(CacheKey.LOGINCOUNT_ + encryString);
		flash.put("loginCount", loginCount == null ? 0 : loginCount);
		
		/* 禁止该页面进行高速缓存 */
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Pragma", "no-cache");
		response.setHeader("Expires", "0");
		
 		render();
	}
	
	/**
	 * 用户登录
	 *
	 * @param mobile 登录名
	 * @param password 登录密码
	 *
	 * @author DaiZhengmiao
	 * @createDate 2016年5月9日
	 */
	public static void logining(String mobile, String password){
		/* 获取当前sessionid */
		String encryString = Session.current().getId();
		
		/* 用于回显 */
		flash.put("name", mobile);
		
		/* 验证信息 */
		if (StringUtils.isBlank(mobile)) {
			flash.error("手机号不能为空");
			
			loginPre();
		}
		if (!StrUtil.isMobileNum(mobile)) { 
			flash.error("手机号格式不正确");
			
			loginPre();
		}
		if (!userService.isMobileExists(mobile)) {
			flash.error("手机号未注册");
			
			loginPre();
		}
		if (StringUtils.isBlank(password)) {
			flash.error("密码不能为空");
			
			loginPre();
		}
		
		/* 获取登录次数 */
		Integer loginCount = (Integer )Cache.get(CacheKey.LOGINCOUNT_ + encryString);
		loginCount = loginCount == null ? 0 : loginCount;
		Cache.set(CacheKey.LOGINCOUNT_+encryString, ++loginCount, Constants.CACHE_TIME_MINUS_30);
		
		
		int securityLockTimes = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.SECURITY_LOCK_TIMES), 3);
		int securityLockTime = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.SECURITY_LOCK_TIME), 30);
//		String pwdEncrypt = Encrypt.MD5(password + ConfConst.ENCRYPTION_KEY_MD5);
		ResultInfo result = userService.logining(mobile, password, Client.WECHAT, getIp(), securityLockTimes, securityLockTime);
		
		if (result.code < 1) {
			if (result.code == ResultInfo.ERROR_SQL) {
				LoggerUtil.info(true, result.msg);
			}
			flash.error(result.msg);
			
			loginPre();
		}
		/* 清除缓存中的登录次数 */
		Cache.delete(CacheKey.LOGINCOUNT_+encryString);
		
		MyAccountCtrl.toAccountHomePre();
	}
	
	/**
	 * 用户登出
	 *
	 *
	 * @author DaiZhengmiao
	 * @createDate 2016年5月9日
	 */
	public static void loginOutPre(){
		ResultInfo result = new ResultInfo();
		CurrUser currUser = getCurrUser();
		if (currUser == null) {
			loginPre();
		}

		result = userService.updateUserLoginOutInfo(currUser.id, Client.WECHAT.code, getIp());
		if (result.code < 0) {
			
			loginPre();
		}
		
		loginPre();
	}
	
	/**
	 * 忘记密码第一步(页面)
	 *
	 * @author huangyunsong
	 * @createDate 2016年6月14日
	 */
	public static void forgetPwdFirstPre(){
		/* 验证码 */
		String randomId = Codec.UUID();
		
		render(randomId);
	}
	
	/**
	 * 忘记密码第一步（手机，验证码）
	 *
	 * @param mobile 手机号
	 * @param smsCode 短信验证码
	 *
	 * @author huangyunsong
	 * @createDate 2016年6月23日
	 */
	public static void forgetPwdFirst(String mobile, String smsCode) {

		if (StringUtils.isBlank(mobile)) {
			flash.error("请输入手机号");
			
			forgetPwdFirstPre();
		}
		
		/* 回显手机号码 */
		flash.put("mobile", mobile);
		
		if (!userService.isMobileExists(mobile)) {
			flash.error("手机号码不存在");
			
			forgetPwdFirstPre();
		}

		/* 短信验证码验证 */
		if (StringUtils.isBlank(smsCode)) {
			flash.error("请输入短信验证码");
			
			forgetPwdFirstPre();
		}
		
		String scene = SmsScene.WX_FORGET_PWD;
		
		/* 获取缓存中的短信验证码 */
    	Object obj = Cache.get(mobile + scene);
		if (obj == null) {
			flash.error("短信验证码已失效");
			
			forgetPwdFirstPre();
		}
		String codeStr = obj.toString();
		
		/*校验短信验证码*/
		if(ConfConst.IS_CHECK_MSG_CODE){
			if (!codeStr.equals(smsCode)) {
				flash.error("短信验证码不正确");
				
				forgetPwdFirstPre();
			}
		}
		
    	String smsCodeSign = Encrypt.encrypt3DES(smsCode, ConfConst.ENCRYPTION_KEY_DES);
		
    	forgetPwdSecondPre(mobile, smsCodeSign);
	}
	
	/**
	 * 发送短信验证码
	 *
	 * @param mobile
	 *
	 * @author huangyunsong
	 * @createDate 2016年6月18日
	 */
	public static void sendCodeOfForgetPWD(String mobile, String randomId, String code) {
		ResultInfo result = new ResultInfo();
		
		if (StringUtils.isBlank(mobile)) {
			result.code = -2;
			result.msg = "手机号不能为空";

			renderJSON(result);
		}
		
		if(!userService.isMobileExists(mobile)){
			result.code = -2;
			result.msg = "手机号未注册";

			renderJSON(result);
		}
		
		if (StringUtils.isBlank(code)) {
			result.code = -3;
			result.msg = "验证码不能为空";
			renderJSON(result);
		}
		
		/* 根据randomId取得对应的验证码值 */
		String RandCode = CaptchaUtil.getCode(randomId);

		/* 验证码失效 */
		if (RandCode == null) {
			result.code = -3;
			result.msg = "验证码已失效";
			renderJSON(result);
		}

		/* 验证码错误 */
		if (ConfConst.IS_CHECK_PIC_CODE) {
			if (!code.equalsIgnoreCase(RandCode)) {
				result.code = -3;
				result.msg = "验证码不正确";
				renderJSON(result);
			}else{
				Cache.delete(randomId);
			}
		}
		
		/* 根据手机号码查询短信发送条数 */
		List<t_send_code> recordByMobile = sendCodeRecordsService.querySendRecordsByMobile(mobile);
		if(recordByMobile.size() >= ConfConst.SEND_SMS_MAX_MOBILE){
			result.code = -4;
			result.msg = "该手机号码单日短信发送已达上限";

			renderJSON(result);
		}
		
		/* 根据IP查询短信发送条数 */
		List<t_send_code> recordByIp = sendCodeRecordsService.querySendRecordsByIp(getIp());
		if(recordByIp.size() >= ConfConst.SEND_SMS_MAX_IP){
			result.code = -4;
			result.msg = "该IP单日短信发送已达上限";

			renderJSON(result);
		}
		
		String scene = SmsScene.WX_FORGET_PWD;
		
		/* 将手机号码存入缓存，用于判断60S中内同一手机号不允许重复发送验证码 */
		String encryString = Session.current().getId();
		Object cache = Cache.get(mobile + encryString + scene);
		if(null == cache){
			Cache.set(mobile + encryString + scene, mobile, Constants.CACHE_TIME_SECOND_60);
		}else{
			String isOldMobile = cache.toString();
			if (isOldMobile.equals(mobile)) {
				result.code = -4;
    			result.msg = "短信已发送";

    			renderJSON(result);
			}
		}
		
		/* 查询短信验证码模板 */
		List<t_template_notice> noticeList = noticeService.queryTemplatesByScene(NoticeScene.SECRITY_CODE);
		
		if(noticeList.size() < 0){
			result.code = -4;
			result.msg = "短信发送失败";

			renderJSON(result);
		}
		String content = noticeList.get(0).content;
		String smsAccount = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_ACCOUNT);
		String smsPassword = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_PASSWORD);
		
		/* 发送短信验证码 */
		SMSUtil.sendCode(smsAccount, smsPassword, mobile, content, Constants.CACHE_TIME_MINUS_30, scene,ConfConst.IS_CHECK_MSG_CODE);
		
		/* 添加一条短信发送控制记录 */
		sendCodeRecordsService.addSendCodeRecords(mobile, getIp());
		
		result.code = 1;
		result.msg = "短信验证码发送成功";
		
		renderJSON(result);
	}
	
	/**
	 * 忘记密码第二步(页面)
	 *
	 * @author huangyunsong
	 * @createDate 2016年6月14日
	 */
	public static void forgetPwdSecondPre(String mobile, String smsCodeSign){

		render(mobile, smsCodeSign);
	}
	
	/**
	 * 忘记密码页面第三步(设置新密码)
	 *
	 * @param mobile
	 * @param password
	 * @param smsCodeSign
	 *
	 * @author huangyunsong
	 * @createDate 2016年6月21日
	 */
	public static void forgetPwdSecond(String mobile, String password, String smsCodeSign){
		
		String scene = SmsScene.WX_FORGET_PWD;
		
		String smsCode = Encrypt.decrypt3DES(smsCodeSign, ConfConst.ENCRYPTION_KEY_DES);
		if (StringUtils.isBlank(smsCode)) {
			flash.error("已超时，请重新操作");
			
			forgetPwdFirstPre();
		}
		
		/* 获取缓存中的短信验证码 */
		String codeStr = "";
    	Object obj = Cache.get(mobile + scene);
		if (obj == null) {
			flash.error("短信验证码已失效");
			
			forgetPwdFirstPre();
		} else {
			codeStr = obj.toString();
			//清除缓存中的短信验证码
    		Cache.delete(mobile + scene);
		}

		/*校验短信验证码*/
		if(ConfConst.IS_CHECK_MSG_CODE){
			if (!codeStr.equals(smsCode)) {
				flash.error("短信验证码不正确");
				
				forgetPwdFirstPre();
			}
		}
		
		long userId = userService.queryIdByMobile(mobile);
		if (userId <= 0) {
			flash.error("用户不存在");
			
			forgetPwdFirstPre();
		}

		if (StringUtils.isBlank(password)) {
			flash.error("请输入密码");

			forgetPwdFirstPre();
		}
		
		if (!StrUtil.isValidPassword(password, 6, 20)) {
			flash.error("密码格式不正确");

			forgetPwdFirstPre();
		}

		t_user user = userService.findByID(userId);
		//用户密码加密无key 
		if(user.is_no_key){
			
			password = Encrypt.MD5(password);
			
			//加密串字母大写
			password = password.toUpperCase();
		}else{
			
			password = Encrypt.MD5(password + ConfConst.ENCRYPTION_KEY_MD5);
		}
		
		/* 密码加密 */
//		password = Encrypt.MD5(password + ConfConst.ENCRYPTION_KEY_MD5);
		ResultInfo result = userService.updatePassword(userId, password);
		if (result.code < 0) {
			flash.error(result.msg);

			forgetPwdFirstPre();
		}
		
		toResultPage(WxPageType.PAGE_UPDATE_PASSWORD_SUCC, "密码修改成功");
	}

}
