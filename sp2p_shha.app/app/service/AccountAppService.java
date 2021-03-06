package service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.shove.Convert;
import com.shove.security.Encrypt;

import common.FeeCalculateUtil;
import common.constants.ConfConst;
import common.constants.Constants;
import common.constants.SettingKey;
import common.enums.AnnualIncome;
import common.enums.Area;
import common.enums.Car;
import common.enums.Client;
import common.enums.Education;
import common.enums.House;
import common.enums.Marital;
import common.enums.NetAssets;
import common.enums.NoticeScene;
import common.enums.Province;
import common.enums.Relationship;
import common.enums.ServiceType;
import common.enums.TradeType;
import common.enums.WorkExperience;
import common.utils.EnumUtil;
import common.utils.Factory;
import common.utils.LoggerUtil;
import common.utils.OrderNoUtil;
import common.utils.PageBean;
import common.utils.ResultInfo;
import common.utils.SMSUtil;
import common.utils.Security;
import controllers.common.BaseController;
import dao.AccountAppDao;
import models.app.bean.BillInvestInfo;
import models.app.bean.BillInvestListBean;
import models.app.bean.DealRecordsBean;
import models.app.bean.MyInvestRecordBean;
import models.app.bean.MyLoanRecordBean;
import models.app.bean.RechargeBean;
import models.app.bean.ReturnMoneyPlanBean;
import models.app.bean.UserBankCard;
import models.app.bean.UserScoreRecordApp;
import models.app.bean.WithdrawBean;
import models.common.bean.UserFundInfo;
import models.common.entity.t_bank_card_user;
import models.common.entity.t_pact;
import models.common.entity.t_pay_pro_city;
import models.common.entity.t_template_notice;
import models.common.entity.t_user;
import models.common.entity.t_user_fund;
import models.common.entity.t_user_info;
import models.core.entity.t_red_packet_user.RedpacketStatus;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import payment.impl.PaymentProxy;
import play.cache.Cache;
import services.common.PactService;
import services.common.SendCodeRecordsService;
import services.common.SettingService;
import services.common.UserFundService;
import services.common.UserService;
import services.core.BillService;
import services.core.CashUserService;
import services.core.RateService;
import services.core.RedpacketUserService;

public class AccountAppService extends UserService{
	
	private static SettingService settingService = Factory.getService(SettingService.class);
	private static SendCodeRecordsService sendCodeRecordsService = Factory.getService(SendCodeRecordsService.class);
	private static UserFundService userFundService = Factory.getService(UserFundService.class);
	private static PactService pactService = Factory.getService(PactService.class);
	private static BillService billService = Factory.getService(BillService.class);
	private static RedpacketUserService redpacketUserService = Factory.getService(RedpacketUserService.class);
	private static AboutUsService aboutAppService = Factory.getService(AboutUsService.class);
	private static RateService rateService= Factory.getService(RateService.class);//加息券
	private static CashUserService cashUserService=Factory.getService(CashUserService.class); //现金券

	private static DebtAppService debtAppService = Factory.getService(DebtAppService.class);
	
	
	private AccountAppDao accountDao;
	private AccountAppService() {
		accountDao = Factory.getDao(AccountAppDao.class);
		super.basedao = accountDao;
	}
	/**
	 * 用户登录（OPT=123）
	 *
	 * @param mobile 手机号
	 * @param password 密码
	 * @param deviceType 设备类型
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年3月31日
	 */
	public String logining(String mobile, String password, String deviceType){
		JSONObject json = new JSONObject();
		ResultInfo result = new ResultInfo();
		
		result = super.findUserInfoByMobile(mobile);
		t_user user = (t_user) result.obj;
		if (user == null) {
			json.put("code", -1);
			json.put("msg", "该用户不存在!");
			
			return json.toString();
		}
		
		int securityLockTimes = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.SECURITY_LOCK_TIMES), 3);
		int securityLockTime = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.SECURITY_LOCK_TIME), 30);
//		String pwdEncrypt = Encrypt.MD5(password + ConfConst.ENCRYPTION_KEY_MD5);
	    result = super.logining(mobile, password, Client.getEnum(Integer.valueOf(deviceType)), BaseController.getIp(), securityLockTimes, securityLockTime);
		
	    if (result.code < 1) {
	    	if(result.code == ResultInfo.ERROR_SQL){
	    		LoggerUtil.info(true, result.msg);
	    	}
	    	
			json.put("code", -1);
			json.put("msg", result.msg);
			
			return json.toString();
		}
		
		json.put("code", 1);
		json.put("msg", "登录成功");
		json.put("userId", user.appSign);
		
		return json.toString();
	}

	/**
	 * 修改登录密码（OPT=122）
	 * @param userId 用户id
	 * @param mobile 手机号
	 * @param newPassword 新密码
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年3月31日
	 */
	public String updateUserPwd(String mobile,long userId, String newPassword) {
		JSONObject json = new JSONObject();	

		ResultInfo result = super.updatePassword(userId, newPassword);
		if (result.code < 1) {
			json.put("code", -1);
			json.put("msg", result.msg);
			
			return json.toString();
		}
		
		json.put("code", 1);
		json.put("msg", "更改成功!");
		
		return json.toString();
	}
		
	/***
	 * 
	 * 发送验证码（OPT=111）
	 * @param mobile  手机号
	 * @param scene  场景
	 * @return
	 * 
	 * @author luzhiwei
	 * @createDate 2016-3-31
	 */
	public String sendCode(String mobile,String scene) {
		
		JSONObject json = new JSONObject();

    	/* 查询短信验证码模板 */
		List<t_template_notice> noticeList = noticeService.queryTemplatesByScene(NoticeScene.SECRITY_CODE);

		if(noticeList == null || noticeList.size() < 1){
			json.put("code", -2);
			json.put("msg", "短信发送失败");

			return json.toString();	
    	}
		String content = noticeList.get(0).content;
		String smsAccount = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_ACCOUNT);
		String smsPassword = settingService.findSettingValueByKey(SettingKey.SERVICE_SMS_PASSWORD);

		/* 发送短信验证码 */
		SMSUtil.sendCode(smsAccount, smsPassword, mobile, content, Constants.CACHE_TIME_MINUS_30, scene,ConfConst.IS_CHECK_MSG_CODE);
		
		/* 添加一条短信发送控制记录 */
		sendCodeRecordsService.addSendCodeRecords(mobile, BaseController.getIp());

		json.put("code", 1);
		json.put("msg", "短信验证码发送成功");
		
		return json.toString();	
	}
	
	/**
	 * 验证验证码（OPT=121）
	 *
	 * @param verificationCode 校验码
	 * @param mobile 手机号
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年3月31日
	 */
	public String verificationCode(String verificationCode, String mobile, String scene) {
		JSONObject json = new JSONObject();
		
		Object obj = Cache.get(mobile + scene);
		if (obj == null) {
			json.put("code", -1);
			json.put("msg", "短信验证码已失效!");
			
			return json.toString();
		}
		String codeStr = obj.toString();
		/** 短信验证码 验证 */
		if(ConfConst.IS_CHECK_MSG_CODE){
			if (!codeStr.equals(verificationCode)) {
				json.put("code", -1);
				json.put("msg", "短信验证码错误!");
				
				return json.toString();
			}
		}
		
		String smsCodeSign = Encrypt.encrypt3DES(verificationCode, ConfConst.ENCRYPTION_KEY_DES);
		
		json.put("code", 1);
		json.put("msg", "验证成功!");
		json.put("smsCodeSign", smsCodeSign);
		json.put("scene", scene);
		
		return json.toString();
	}

	/**
	 * 查询提现记录（OPT=215）
	 *
	 * @param userId 用户id
	 * @param currPage 当前请求页
	 * @param pageNum 页大小
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年3月31日
	 */
	public String pageOfWithdrawRecord (long userId, int currPage, int pageNum) {
		
		PageBean<WithdrawBean> page = accountDao.pageOfWithdrawRecord(userId, currPage, pageNum);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功!");
		map.put("records", page.page);
		
		return JSONObject.fromObject(map).toString();
	}
	
	/***
	 * 
	 * 个人基本信息(OPT=251)
	 * @return
	 * @param userId 用户Id
	 * @param 
	 * @author luzhiwei
	 * @createDate 2016-3-31
	 */
	public String findUserInfomation (long userId){
		JSONObject json = new JSONObject();
		
		/**获取用户基本信息**/
		t_user_info userInfo = userInfoService.findUserInfoByUserId(userId);
		
		/**获取用户资产信息**/
		UserFundInfo userFundInfo = super.findUserFundInfo(userId);
		
		/** 获取是否有未读信息  */
		boolean hasUserUnreadMsg = accountDao.hasUserUnreadMsg(userId);
		
		/** 可用红包 */
		double totalUserRedPacket = redpacketUserService.totalUserRedPacket(userId, RedpacketStatus.UNUSED.code);
		
		/**可用加息卷总利率*/
		double RateSum=rateService.userRateSum(userId);
		
		/**现金券总金额*/
		double cashSum=cashUserService.UserCashSum(userId);
		
		/**用户可用积分*/
		int scoreBalance = (int)userFundService.findUserScoreBalance(userId);
		
		String contactUs = aboutAppService.findContactUs();
		
		JSONObject obj = JSONObject.fromObject(contactUs);
		
		
		json.put("name", userInfo.name);
		json.put("photo", userInfo.photo);
		json.put("totalIncome", userFundInfo.total_income);//总收益
		json.put("totalAssets", userFundInfo.total_assets);//总资产
		json.put("freeze", userFundInfo.freeze);//冻结
		json.put("balance",userFundInfo.balance);//可用余额
		json.put("noReceive",userFundInfo.no_receive);//待收的
		json.put("totalUserRedPacket",totalUserRedPacket);//红包可用余额
		
		json.put("RateSum", RateSum);//加息券总利率
		json.put("cashSum", cashSum);//现金券总金额
		json.put("scoreBalance", scoreBalance);
		
		json.put("record", "/wx/home/platformdata.html");//平台H5
		json.put("hasUserUnreadMsg", hasUserUnreadMsg);
		json.put("tel", obj.get("companyTel"));//客服电话
		json.put("code", 1);
		json.put("msg", "查询成功!");
		
		return json.toString();
	}
	
	/****
	 *	充值记录接口（OPT=212）
	 * @param userId 用户id
	 * @param currPage 
	 * @param pageSize 
	 * @return
	 *
	 * @author luzhiwei
	 * @createDate 2016-3-31
	 */
	public String pageOfRechargeRecord (long userId,int currPage,int pageSize) throws Exception{
		
		PageBean<RechargeBean> rechargePage = accountDao.pageOfRechargeRecord(userId, currPage, pageSize);

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功!");
		map.put("records", rechargePage.page);
		
		return JSONObject.fromObject(map).toString();
	}

	/**
	 * 消息列表（OPT=252）
	 *
	 * @param userId 用户id
	 * @param curr
	 * @param page
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月1日
	 */
	public String pageOfUserMessage (long userId, int currPage,int pageSize) {
		
		PageBean<Map<String, Object>> rechargePage = accountDao.pageOfUserMessage(userId, currPage, pageSize);
		
		//更新消息为已读
		accountDao.editUserMsgStatus(userId, currPage, pageSize);
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功!");
		map.put("records", rechargePage.page);
		
		return JSONObject.fromObject(map).toString();
	}
		
	/****
	 * 充值准备（opt=216）
	 *
	 * @return
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-12
	 */
	public static String rechargePre(){
		JSONObject json = new JSONObject();
		/* 最高充值金额 */
		int rechargeHighest = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.RECHARGE_AMOUNT_MAX), 0);
		
		/* 最低充值金额 */
		int rechargeLowest = Convert.strToInt(settingService.findSettingValueByKey(SettingKey.RECHARGE_AMOUNT_MIN), 0);
		
		json.put("rechargeHighest", rechargeHighest);
		json.put("rechargeLowest", rechargeLowest);
		json.put("code",1);
		json.put("msg", "查询成功");
		
		return json.toString();
	}
	
	/****
	 * 
	 * 充值接口(OPT=211)
	 * @param userId 用户id
	 * @param amount 金额
	 * @param client 客户端
	 * @param result
	 * @return
	 * @throws Exception
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-1
	 */
	public ResultInfo recharge (long userId ,double amount ,Client client) throws Exception{
		ResultInfo result = new ResultInfo();
		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.RECHARGE);
		
		result = userFundService.recharge(userId, serviceOrderNo, amount, "账户直充", client);
		if (result.code < 1) {
			result.code = -1;
			
			return result;
		}

		if (ConfConst.IS_TRUST) {
			result = PaymentProxy.getInstance().recharge(client.code, serviceOrderNo, userId, amount);
			if (result.code < 0) {
				result.code = -1;
				
				return result;
			}
			result.code = 1;
			result.msg = "App充值请求提交中";
			
			return result;
		}
		
		if (!ConfConst.IS_TRUST){
			//普通网关模式
			result = userFundService.doRecharge(userId, amount, serviceOrderNo);
			if (result.code < 1) {
				result.code = -1;
				
				return result;
			}
		}
		
		result.code = 1;
		
		return result;
	}
	
	/****
	 * 
	 * 充值接口(OPT=211)
	 * @param userId 用户id
	 * @param amount 金额
	 * @param client 客户端
	 * @param result
	 * @return
	 * @throws Exception
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-1
	 */
	public ResultInfo directRecharge(long userId, double amount, TradeType tradeType, String smsCode,
			String smsSeq, String bankId, Client client) throws Exception{
		ResultInfo result = new ResultInfo();
		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.RECHARGE);
		
		result = userFundService.recharge(userId, serviceOrderNo, amount, "账户直充", client);
		if (result.code < 1) {
			result.code = -1;
			
			return result;
		}

		if (ConfConst.IS_TRUST) {
			result = PaymentProxy.getInstance().directRecharge(client.code, serviceOrderNo, userId, tradeType, bankId, amount, smsCode, smsSeq, null);
			if (result.code < 0) {
				result.code = -1;
				
				return result;
			}
			result.code = 1;
			result.msg = "App充值请求提交中";
			
			return result;
		}
		
		if (!ConfConst.IS_TRUST){
			//普通网关模式
			result = userFundService.doRecharge(userId, amount, serviceOrderNo);
			if (result.code < 1) {
				result.code = -1;
				
				return result;
			}
		}
		
		result.code = 1;
		
		return result;
	}
	
	/**
	 * 分页查询 回款计划（OPT=242）
	 * @param currPage
	 * @param pageSize
	 * @param userId 用户id
	 * @return
	 *
	 * @author luzhiwei
	 * @createDate 2016年4月1日
	 *
	 */
	public String pageOfInvestReceive (int currPage, int pageSize, long userId) throws Exception{
		
		PageBean<ReturnMoneyPlanBean> page = accountDao.pageOfInvestReceive(userId, currPage, pageSize);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功");
		map.put("records", page.page);
		
		return JSONObject.fromObject(map).toString();
	}
		
	/***
	 * 我的投资接口（OPT=231）
	 * @param currPage
	 * @param pageSize
	 * @param userId 用户id
	 * @return
	 * 
	 * @author luzhiwei
	 * @createDate 2016-4-1
	 */
	public PageBean<MyInvestRecordBean> pageOfMyInvestRecord(int currPage, int pageSize, long userId) throws Exception{
		ResultInfo result = null;
		
		PageBean<MyInvestRecordBean> page = accountDao.pageOfMyInvestRecord(currPage, pageSize ,userId);
		if (page != null && page.page != null && page.page.size() > 0) {
			for (MyInvestRecordBean bean : page.page) {
				result= debtAppService.isInvestCanbeTransfered(Long.valueOf(bean.investOriId));
				if(result.code == 1){
					bean.canBeTransed = true;
				}
			}
		}
		return page;
	}
	
	/***
	 * 投资账单接口（OPT=232）
	 * @param userId 用户id
	 * @param investId 投资id
	 * 
	 * @author luzhiwei
	 * @createDate 2016-4-5
	 */
	public String listOfInvestBillRecord(long userId,long investId) throws Exception{

		List<BillInvestListBean> investBillList = accountDao.listOfInvestBillRecord(userId,investId);

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功");
		map.put("billList",investBillList);

		return JSONObject.fromObject(map).toString();
	}
	
	/***
	 * 投资账单详情接口（OPT=237）
	 * @param billInvestId 理财账单id
	 * 
	 * @author luzhiwei
	 * @createDate 2016-4-5
	 */
	public String findInvestBillRecord(long billInvestId,long bidId) throws Exception{
		BillInvestInfo billInvestInfo = accountDao.findInvestBillRecord(billInvestId);
		int totalPeriod = billService.findBidTotalBillCount(bidId);
		
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("totalPeriod", totalPeriod);
		map.put("code", 1);
		map.put("msg", "查询成功");
		map.put("billInvestInfo",billInvestInfo);

		return JSONObject.fromObject(map).toString();
	}

	/**
	 * 我的借款（OPT=233）
	 *
	 * @param userId 用户id
	 * @param currPage
	 * @param pageSize
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月5日
	 */
	public PageBean<MyLoanRecordBean> pageOfMyLoan(long userId, int currPage, int pageSize) {
		
		PageBean<MyLoanRecordBean> myLoan = accountDao.pageOfMyLoan(userId, currPage, pageSize);


		return myLoan;
	}
		
	/****
	 * 绑卡接口（OPT=222）
	 *
	 * @param userId 用户id
	 * @throws Exception
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-5
	 */
	public ResultInfo bindCard(long userId,int client) throws Exception{	
		ResultInfo result = new ResultInfo();
		
		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.BIND_CARD);
		
		result = PaymentProxy.getInstance().userBindCard(client, serviceOrderNo, userId);
		
	    if(result.code < 0){
	    	result.code = -1;
			
			return result;
	    }
	    
		result.code = 1;
		result.msg = "App绑卡请求提交中";
		
		return result;
	}
	
    /***
     * 
     * 交易记录接口（OPT=241）
     * @param userId 用户id
     * @param currPage
     * @param pageSize
     * @return
     * @description 
     *
     * @author luzhiwei
     * @createDate 2016-4-6
     */
	public String pageOfUserDealRecords(long userId,int currPage,int pageSize){
		
		PageBean<DealRecordsBean> myLoan = accountDao.pageOfUserDealRecords(userId, currPage, pageSize);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功");
		map.put("records", myLoan.page);

		return JSONObject.fromObject(map).toString();	
	}
	
	 /***
     * 
     * 积分记录接口（OPT=1201）
     * @param userId 用户id
     * @param currPage
     * @param pageSize
     * @return
     * @description 
     *
     * @author luzhiwei
     * @createDate 2016-4-6
     */
	public String pageOfUserScoreRecord(long userId,int currPage,int pageSize){		
		PageBean<UserScoreRecordApp> myLoan = accountDao.pageOfUserScoreRecord( currPage, pageSize, userId);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("code", 1);
		map.put("msg", "查询成功");
		map.put("records", myLoan.page);
		return JSONObject.fromObject(map).toString();	
	}

	/****
	 * 客户端获取会员信息接口（OPT=253）
	 *
	 * @param userId 用户id
	 * @throws Exception
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-5
	 */
	public String findUserInfo(long userId) throws Exception{	
		JSONObject json = new JSONObject();
		Map<String, Object> userInfo = accountDao.findUserInfo(userId);
        
		json.put("userInfo", JSONObject.fromObject(userInfo));
		json.put("education", EnumUtil.parseEnum(Education.values()));
		json.put("marital",EnumUtil.parseEnum(Marital.values()));
		json.put("workExperience", EnumUtil.parseEnum(WorkExperience.values()));
 		json.put("annualIncome",EnumUtil.parseEnum(AnnualIncome.values()));
 		json.put("netAssets",EnumUtil.parseEnum(NetAssets.values()));
		json.put("car", EnumUtil.parseEnum(Car.values()));
		json.put("house",EnumUtil.parseEnum(House.values()));
		json.put("relationship",EnumUtil.parseEnum(Relationship.values()));
		
		//TODO  添加省级、市级名
		Province province=Province.getProvByCode((String)userInfo.get("prov_id"));
		String newArea=(String)userInfo.get("area_id");
		Area area=Area.getAreaByCode(newArea==null?"":newArea);
		json.put("provinceName", province!=null?province.name:"");
		json.put("areaName", area!=null?area.name:"");
		
		json.put("code",1);
		json.put("msg", "查询成功");
		
		return json.toString();
	}
	/***
	 * 
	 *	客户端保存会员信息接口（OPT=254）
	 * @param userId 用户id
	 * @param education 学历
	 * @parma mairtal 婚姻
	 * @param workExperience 工作 
	 * @param annualIncome 年收入
	 * @param netAsset  资产
	 * @param car 车
	 * @param house 房
	 * @param emergencyContactType 紧急联系人关系
	 * @param emergencyContactName  紧急联系人姓名
	 * @param emergencyContactMobile 紧急联系人电话
	 * @return
	 * @throws Exception
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-6
	 */
	/*public ResultInfo updateUserInfo(long userId, int education, int marital, int workExperience,int annualIncome,int netAsset,int car,int house,int emergencyContactType,String emergencyContactName,String emergencyContactMobile) throws Exception{*/
	public ResultInfo updateUserInfo( String prov_id,String area_id,String work_unit,double registered_fund,Date start_time,long userId, int education, int marital, int workExperience,int annualIncome,int netAsset,int car,int house,int emergencyContactType,String emergencyContactName,String emergencyContactMobile) throws Exception{
		ResultInfo result = new ResultInfo();
		/*int rows = accountDao.updateUserInfo(userId , education, marital, workExperience ,annualIncome ,netAsset ,car ,house , emergencyContactType ,emergencyContactName ,emergencyContactMobile );*/
		int rows = accountDao.updateUserInfo(prov_id,area_id,work_unit,registered_fund,start_time,userId , education, marital, workExperience ,annualIncome ,netAsset ,car ,house , emergencyContactType ,emergencyContactName ,emergencyContactMobile );
		if (rows < 0) {
			result.code = -1;
			result.msg = "保存会员信息失败";
			
			return result;
		}

		result.code = 1;
		result.msg = "保存会员信息成功";
		
		return result;
	}
	
	/**
	 * 安全中心（opt=261）
	 *
	 * @param userId 用户id
	 * 改版添加  cardNo银行卡号  bankName银行名称   photoUrl用户头像路径   address 地址
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月6日
	 */
	public String findUserSecurity(long userId) {
		JSONObject json = new JSONObject();
		
		t_user_fund userFund = userFundService.queryUserFundByUserId(userId);
		t_user_info userInfo = userInfoService.findUserInfoByUserId(userId);
		t_user user = super.findByID(userId);
		List<t_bank_card_user> cardList = bankCardUserService.queryCardByUserId(userId);
		t_bank_card_user  bankCard= null;
//		StringBuffer address=new StringBuffer();
	
		json.put("paymentAccount", userFund.payment_account == null ? "" : userFund.payment_account);
		json.put("realityName", userInfo.reality_name == null ? "" : userInfo.reality_name);
		json.put("mobile", user.mobile == null ? "" : userInfo.mobile );
		json.put("email", userInfo.email == null ? "" : userInfo.email);
		json.put("card", cardList==null || cardList.size()==0 ? "" : cardList.size() ) ;
	
		//新版添加
	if(cardList.size()>0 ){//银行卡号、银行名称
		bankCard=cardList.get(0);
		json.put("cardNo", bankCard.account==null?"":bankCard.account );
		json.put("bankName", bankCard.bank_name==null?"":bankCard.bank_name);
	}else{
		json.put("cardNo", "" );	
		json.put("bankName","");
	}
		
		



		// 状态激活
		json.put("activited", userFund.is_actived);
		json.put("code", 1);
		json.put("msg", "查询成功");
		
		return json.toString();
	}
	
	/**
	 * 根据原密码修改密码（OPT=262）
	 *
	 * @param userId 用户id
	 * @param oldPassword 旧密码
	 * @param password 新密码
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月6日
	 */
	public ResultInfo userUpdatePwdbyold(long userId, String oldPassword, String password) {
		ResultInfo result = new ResultInfo();
		
		t_user user = super.findByID(userId);
		if (!oldPassword.equalsIgnoreCase(user.password)) {
			result.code = -1;
			result.msg = "原密码不正确";
			
			return result;
		}
		
		result = super.updatePassword(userId, password);
		if (result.code < 1) {
			result.code = -1;
			
			return result;
		}
		
		result.code = 1;
		result.msg = "修改成功";
		
		return result;
	}
	
	/**
	 * 提现准备（opt=213）
	 *
	 * @param userId 用户id
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月7日
	 */
	public String withdrawalPre(long userId) throws Exception{
		JSONObject json = new JSONObject();
		
		List<UserBankCard> userBankCard = accountDao.listOfUserBankCard(userId);
		
		/** 提现手续费起点 */
		String withdrawFeePoint = settingService.findSettingValueByKey(SettingKey.WITHDRAW_FEE_POINT);
		/** 提现手续费率 */
		String withdrawFeeRate = settingService.findSettingValueByKey(SettingKey.WITHDRAW_FEE_RATE);
		/** 最低提现手续费 */
		String withdrawFeeMin = settingService.findSettingValueByKey(SettingKey.WITHDRAW_FEE_MIN);
		
		t_user_fund userFundInfo = userFundService.queryUserFundByUserId(userId);
		
		json.put("code", 1);
		json.put("msg", "查询成功");
		json.put("withdrawFeePoint", withdrawFeePoint);
		json.put("withdrawFeeRate", withdrawFeeRate);
		json.put("withdrawFeeMin", withdrawFeeMin);
		json.put("withdrawMaxRate", Constants.WITHDRAW_MAXRATE);
		json.put("balance", userFundInfo.balance);
		json.put("maxWithdraw", 200000); //最大提现金额 ，由于PC端是固定的，App暂时先固定
		json.put("records", JSONArray.fromObject(userBankCard));
		
		return json.toString();
	}
	
	/***
	 * 
	 * 银行卡列表(opt=221)
	 * @param userId 用户id
	 * @return
	 * @throws Exception
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-7
	 */
	public String listOfUserBankCard(long userId) throws Exception{
		JSONObject json = new JSONObject();
		List<UserBankCard> userBankList = accountDao.listOfUserBankCard(userId);
		
		json.put("code", 1);
		json.put("msg", "查询成功!");
		json.put("records", userBankList);
		
		return json.toString();
	}
	
	/**
	 * 提现（opt=214）
	 *
	 * @param userId
	 * @param withdrawalAmt 提现金额
	 * @param bankAccount 卡
	 * @param client 设备端
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月7日
	 */
	// TODO ADD CASHTYPE
	public ResultInfo withdrawal(long userId, double withdrawalAmt,	String bankAccount, String cashType, int client) {
		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.WITHDRAW);
		
		ResultInfo result = userFundService.withdrawal(userId, serviceOrderNo, withdrawalAmt, bankAccount, Client.getEnum(client));
		if (result.code < 1) {
			result.code = -1;

			return result;
		}
		
		if (ConfConst.IS_TRUST) {
			result = PaymentProxy.getInstance().withdrawal(client, serviceOrderNo, userId, withdrawalAmt, bankAccount, cashType);
			if (result.code < 1) {
				result.code = -1;

				return result;
			}
			result.code = 1;
			result.msg = "App提现提交中...";

			return result;
		}
		
		if (!ConfConst.IS_TRUST){
			//提现手续费
			double withdrawalFee = FeeCalculateUtil.getWithdrawalFee(withdrawalAmt);
			
			//普通网关模式
			result = userFundService.doWithdrawal(userId, withdrawalAmt, withdrawalFee, withdrawalFee, serviceOrderNo, false);
			if (result.code < 1) {
				result.code = -1;
	
				return result;
			}
		}
		
		result.code = 1;
		result.msg = "提现成功";

		return result;
	}
	
	/**
	 * 开户准备
	 * @param userId
	 * @param deviceType
	 * @return
	 */
	public String createAccountPre(long userId,int deviceType) {
		JSONObject json = new JSONObject();
		
		// List<Map<String, Object>> proCityList = JPAUtil.getList("select * from t_pay_pro_city group by prov_num");
		
		/*Map<String, Object> bankMap = new HashMap<String,Object>();
		bankMap.put("0102", "中国工商银行");
		bankMap.put("0103", "中国农业银行");
		bankMap.put("0104", "中国银行");
		bankMap.put("0105", "中国建设银行");
		bankMap.put("0302", "中信实业银行");
		bankMap.put("0303", "中国光大银行");
		bankMap.put("0304", "华夏银行");
		bankMap.put("0305", "中国民生银行");
		bankMap.put("0306", "广东发展银行");
		bankMap.put("0307", "平安银行股份有限公司");
		bankMap.put("0308", "招商银行");
		bankMap.put("0309", "兴业银行");
		bankMap.put("0310", "上海浦东发展银行");
		bankMap.put("0403", "国家邮政局邮政储汇局");*/
		
		json.put("code", 1);
		json.put("msg", "查询成功");
		//json.put("proCityList", proCityList);
		//json.put("bankMap", bankMap);
		
		return json.toString();
	}
	
	/**
	 * 资金存管开户
	 *
	 * @param userId 用户id
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月12日
	 */
	public String createAccount(long userId,int deviceType) {
		JSONObject json = new JSONObject();
		
		t_user_fund userFund = userFundService.queryUserFundByUserId(userId);
		if (userFund == null) {
			json.put("code", -1);
			json.put("msg", "糟糕！没有获取到该用户信息");

			return json.toString();
		}
		
		if (StringUtils.isNotBlank(userFund.payment_account)) {
			json.put("code", -1);
			json.put("msg", "你已开通资金存管");

			return json.toString();
		}

		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.REGIST);
		
		ResultInfo result = PaymentProxy.getInstance().regist(deviceType, serviceOrderNo, userId);
		
		if (result.code < 1) {
			json.put("code", -1);
			json.put("msg", result.msg);

			return json.toString();
		}
		
		json.put("html", result.obj.toString());
		json.put("code", 1);
		json.put("msg", "开户请求处理中");

		return json.toString();
	}
	
	/**
	 * 上海银行资金存管开户
	 *
	 * @author menghuijia
	 * @createDate 2017年7月11日
	 */
	public String createAccount(long userId, String hfName, String realName, String idNumber, String mobile,
			String cardId, String bankId, String provId, String areaId, String smsCode, String smsSeq, int deviceType) {
		JSONObject json = new JSONObject();
		
		t_user_fund userFund = userFundService.queryUserFundByUserId(userId);
		if (userFund == null) {
			json.put("code", -1);
			json.put("msg", "糟糕！没有获取到该用户信息");

			return json.toString();
		}
		
		if (StringUtils.isNotBlank(userFund.payment_account)) {
			json.put("code", -1);
			json.put("msg", "你已开通资金存管");

			return json.toString();
		}

		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.REGIST);
		
		ResultInfo result = PaymentProxy.getInstance().userRegist(deviceType, serviceOrderNo, userId,
				hfName, realName, idNumber, mobile, cardId, bankId, provId, areaId, smsCode, smsSeq);
		
		if (result.code < 1) {
			json.put("code", -1);
			json.put("msg", result.msg);

			return json.toString();
		}
		
		json.put("html", result.obj.toString());
		json.put("code", 1);
		json.put("msg", "开户请求处理中");

		return json.toString();
	}
	
	/**
	 * 富有专用资金存管开户
	 *
	 * @param userId 用户id
	 * @return
	 *
	 */
	public String createAccount(long userId, String provNum, String cityNum, String bankType, String bankName, String bankNum, int deviceType) {
		JSONObject json = new JSONObject();
		
		t_user_fund userFund = userFundService.queryUserFundByUserId(userId);
		if (userFund == null) {
			json.put("code", -1);
			json.put("msg", "糟糕！没有获取到该用户信息");

			return json.toString();
		}
		
		if (StringUtils.isNotBlank(userFund.payment_account)) {
			json.put("code", -1);
			json.put("msg", "你已开通资金存管");

			return json.toString();
		}

		//业务订单号
		String serviceOrderNo = OrderNoUtil.getNormalOrderNo(ServiceType.REGIST);
		
		t_user_info userInfo = userInfoService.findUserInfoByUserId(userId);
		
		ResultInfo result = PaymentProxy.getInstance().regist(deviceType, serviceOrderNo, userId, userInfo.reality_name, userInfo.id_number,
				userInfo.mobile, userInfo.email, cityNum, bankType, bankName, bankNum);
		
		if (result.code < 1) {
			json.put("code", -1);
			json.put("msg", result.msg);

			return json.toString();
		}
		
		json.put("html", "");
		json.put("code", 1);
		json.put("msg", "开户成功");

		return json.toString();
	}

	/**
	 * 根据省份查询城市列表
	 * @param provinceId
	 * @return
	 */
	public String queryCity(String provinceId){
		JSONObject json = new JSONObject();
		
		List<Map<String, Object>> cityList = t_pay_pro_city.find("prov_num = ? ", provinceId).fetch();
		
		json.put("code", 1);
		json.put("msg", "查询成功");
		json.put("cityList", cityList);
		
		return json.toString();
	}
	/**
	 * 设置默认银行卡（opt=223）
	 *
	 * @param bankCardId 银行卡id
	 * @param userId 用户id
	 * @return
	 *
	 * @author yaoyi
	 * @createDate 2016年4月12日
	 */
	public String setDefaultBankCard(long bankCardId, long userId) {
		JSONObject json = new JSONObject();
		
		int isFlag = bankCardUserService.updateUserCardStatus(bankCardId, userId);
		if (isFlag < 1) {
			json.put("code", -1);
			json.put("msg", "设置失败!");

			return json.toString();
		}
		
		json.put("code", 1);
		json.put("msg", "设置成功!");

		return json.toString();
	}
	
	/***
	 * 查看合同（opt=236）
	 *
	 * @param bidId 标id
	 * @return
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-12
	 */
	public String findBidPact(long bidId){
		JSONObject json = new JSONObject();
		t_pact pact = pactService.findByBidid(bidId);
		if(pact == null){
			json.put("code", -1);
			json.put("msg", "加载失败");

			return json.toString();
		}
		json.put("html", pact.content);
		json.put("code", 1);
		json.put("msg", "加载成功");
		
		return json.toString();
	}
	
	/**
	 * 
	 * 注册准备
	 * @return
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-14
	 */
	public String registerPre(){
		JSONObject json = new JSONObject();
	
		String title = accountDao.findRegisterDealTitle();
		json.put("code", 1);
		json.put("msg", "查询成功");
		json.put("title", title);
		
		return json.toString();
	}
	
	/***
	 * 获取平台ico logo
	 *
	 * @return
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-14
	 */
	public String findPlatformIconFilename() {
		JSONObject json = new JSONObject();
		
		json.put("code", 1);
		json.put("msg", "查询成功");
		String platform_icon_filename = settingService.findSettingValueByKey(SettingKey.PLATFORM_ICON_FILENAME);
		json.put("platformIconFileName", platform_icon_filename);
		
		return json.toString();
	}
	
	/***
	 * 修改邮箱
	 * @param userId 用户id
	 * @param email 邮箱
	 * @return
	 * @description 
	 *
	 * @author luzhiwei
	 * @createDate 2016-4-14
	 */
	public String updateEmail(long userId,String email){
		JSONObject json = new JSONObject();
		
		/**获取用户基本信息**/
		t_user_info userInfo = userInfoService.findUserInfoByUserId(userId);
		
		/* 用户Id进行加密 */
		String userSign = Security.addSign(userId, Constants.USER_ID_SIGN, ConfConst.ENCRYPTION_KEY_DES);
		
		/* 邮箱进行加密 */
		String emailStr = Security.addEmailSign(email, Constants.MSG_EMAIL_SIGN, ConfConst.ENCRYPTION_KEY_DES);
		
		/* 获取修改邮箱URL */
		String url = BaseController.getBaseURL() + "loginAndRegiste/confirmactiveemail.html?su=" + userSign +"&se="+emailStr;

		/* 发送邮件激活 */
		ResultInfo result = noticeService.sendReBindEmail(email, userInfo.name, url);
		if (result.code < 1) {
			json.put("code", -1);
			json.put("msg", result.msg);

			return json.toString();
		}
		json.put("code", 1);
		json.put("msg", "邮件发送成功,请到邮箱确认");

		return json.toString();
	}

}
