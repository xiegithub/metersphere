package io.metersphere.service;

import io.metersphere.base.domain.SystemParameter;
import io.metersphere.base.domain.SystemParameterExample;
import io.metersphere.base.mapper.SystemParameterMapper;
import io.metersphere.commons.constants.ParamConstants;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.EncryptUtils;
import io.metersphere.i18n.Translator;
import io.metersphere.ldap.domain.LdapInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.mail.MessagingException;
import java.util.*;


@Service
public class SystemParameterService {

    @Resource
    private SystemParameterMapper systemParameterMapper;

    public String getSystemLanguage() {
        String result = StringUtils.EMPTY;
        SystemParameterExample example = new SystemParameterExample();
        example.createCriteria().andParamKeyEqualTo(ParamConstants.I18n.LANGUAGE.getValue());
        List<SystemParameter> list = systemParameterMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(list)) {
            String value = list.get(0).getParamValue();
            if (StringUtils.isNotBlank(value)) {
                result = value;
            }
        }
        return result;
    }

    public void editMail(List<SystemParameter> parameters) {
        List<SystemParameter> paramList = this.getParamList(ParamConstants.Classify.MAIL.getValue());
        boolean empty = paramList.size() < 2;
        parameters.forEach(parameter -> {
            if (parameter.getParamKey().equals(ParamConstants.MAIL.PASSWORD.getKey())) {
                String string = EncryptUtils.aesEncrypt(parameter.getParamValue()).toString();
                parameter.setParamValue(string);
            }
            if (empty) {
                systemParameterMapper.insert(parameter);
            } else {
                systemParameterMapper.updateByPrimaryKey(parameter);
            }
        });
    }

    public List<SystemParameter> getParamList(String type) {
        SystemParameterExample example = new SystemParameterExample();
        example.createCriteria().andParamKeyLike(type + "%");
        return systemParameterMapper.selectByExample(example);
    }

    public void testConnection(HashMap<String, String> hashMap) {
        JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setDefaultEncoding("UTF-8");
        javaMailSender.setHost(hashMap.get(ParamConstants.MAIL.SERVER.getKey()));
        javaMailSender.setPort(Integer.valueOf(hashMap.get(ParamConstants.MAIL.PORT.getKey())));
        javaMailSender.setUsername(hashMap.get(ParamConstants.MAIL.ACCOUNT.getKey()));
        javaMailSender.setPassword(hashMap.get(ParamConstants.MAIL.PASSWORD.getKey()));
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        if (BooleanUtils.toBoolean(hashMap.get(ParamConstants.MAIL.SSL.getKey()))) {
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (BooleanUtils.toBoolean(hashMap.get(ParamConstants.MAIL.TLS.getKey()))) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        javaMailSender.setJavaMailProperties(props);
        try {
            javaMailSender.testConnection();
        } catch (MessagingException e) {
            MSException.throwException(Translator.get("connection_failed"));
        }
    }

    public String getVersion() {
        return System.getenv("MS_VERSION");
    }

    public Object mailInfo(String type) {
        List<SystemParameter> paramList = this.getParamList(type);
        if (CollectionUtils.isEmpty(paramList)) {
            paramList = new ArrayList<>();
            ParamConstants.MAIL[] values = ParamConstants.MAIL.values();
            for (ParamConstants.MAIL value : values) {
                SystemParameter systemParameter = new SystemParameter();
                if (value.equals(ParamConstants.MAIL.PASSWORD)) {
                    systemParameter.setType(ParamConstants.Type.PASSWORD.getValue());
                } else {
                    systemParameter.setType(ParamConstants.Type.TEXT.getValue());
                }
                systemParameter.setParamKey(value.getKey());
                systemParameter.setSort(value.getValue());
                paramList.add(systemParameter);
            }
        } else {
            paramList.stream().filter(param -> param.getParamKey().equals(ParamConstants.MAIL.PASSWORD.getKey())).forEach(param -> {
                String string = EncryptUtils.aesDecrypt(param.getParamValue()).toString();
                param.setParamValue(string);
            });
        }
        paramList.sort(Comparator.comparingInt(SystemParameter::getSort));
        return paramList;
    }

    public void saveLdap(List<SystemParameter> parameters) {
        SystemParameterExample example = new SystemParameterExample();
        parameters.forEach(param -> {
            if (param.getParamKey().equals(ParamConstants.LDAP.PASSWORD.getValue())) {
                String string = EncryptUtils.aesEncrypt(param.getParamValue()).toString();
                param.setParamValue(string);
            }
            example.createCriteria().andParamKeyEqualTo(param.getParamKey());
            if (systemParameterMapper.countByExample(example) > 0) {
                systemParameterMapper.updateByPrimaryKey(param);
            } else {
                systemParameterMapper.insert(param);
            }
            example.clear();
        });
    }

    public LdapInfo getLdapInfo(String type) {
        List<SystemParameter> params = getParamList(type);
        LdapInfo ldap = new LdapInfo();
        if (!CollectionUtils.isEmpty(params)) {
            for (SystemParameter param : params) {
                if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.URL.getValue())) {
                    ldap.setUrl(param.getParamValue());
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.DN.getValue())) {
                    ldap.setDn(param.getParamValue());
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.PASSWORD.getValue())) {
                    String password = EncryptUtils.aesDecrypt(param.getParamValue()).toString();
                    ldap.setPassword(password);
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.OU.getValue())) {
                    ldap.setOu(param.getParamValue());
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.FILTER.getValue())) {
                    ldap.setFilter(param.getParamValue());
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.MAPPING.getValue())) {
                    ldap.setMapping(param.getParamValue());
                } else if (StringUtils.equals(param.getParamKey(), ParamConstants.LDAP.OPEN.getValue())) {
                    ldap.setOpen(param.getParamValue());
                }
            }
        }
        return ldap;
    }

    public String getValue(String key) {
        SystemParameter param = systemParameterMapper.selectByPrimaryKey(key);
        if (param == null) {
            return null;
        }
        return param.getParamValue();
    }
}
