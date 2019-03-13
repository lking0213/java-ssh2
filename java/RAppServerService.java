/**
 * Copyright &copy; 2015-2020 All rights reserved.
 */
package com.set.trms.rappserver.service;


import com.set.direwolf.mybatis.CrudService;
import com.set.direwolf.web.OperationResult;
import com.set.trms.rappserver.dao.RAppServerDao;
import com.set.trms.rappserver.entity.RAppServer;
import com.set.trms.serverinfo.dao.ServerInfoDao;
import com.set.trms.serverinfo.entity.ServerInfo;
import com.set.trms.util.SSH2Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;

/**
 * 应用-服务器-关系Service
 *
 * @author qxx
 * @version 2019-1-14
 */
@Service
@Transactional(readOnly = true)
public class RAppServerService extends CrudService<RAppServerDao, RAppServer, Integer> {
    @Autowired
    private ServerInfoDao serverInfoDao;

    /**
     * 通过应用部署ID，启动或停止服务
     *
     * @param id          应用部署ID
     * @param operateType 01:启动服务 02：停止服务 03：下载日志
     * @throws Exception
     */
    public OperationResult operateService(HttpServletResponse response, Integer id, String operateType) {
        if (id == null) {
            return OperationResult.buildFailureResult("应用部署ID为空");
        }
        RAppServer ras = new RAppServer();
        ras.setAppId(id);
        List<RAppServer> list = this.dao.findList(ras);
        if (list.size() == 0) {
            return OperationResult.buildFailureResult("应用未关联服务器");
        }
        ras = list.get(0);
        if (ras.getServerId() == null || ras.getServicePath() == null || ras.getServicePort() == null || ras.getServiceType() == null) {
            return OperationResult.buildFailureResult("应用与服务器关联信息缺失");
        }
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setId(ras.getServerId());
        serverInfo = serverInfoDao.findList(serverInfo).get(0);
        if (serverInfo == null || serverInfo.getUsername() == null || serverInfo.getPassword() == null || serverInfo.getOsType() == null) {
            return OperationResult.buildFailureResult(ras.getServerId() + "服务器信息缺失");
        }
        try {
            String command = "";
            SSH2Util ssh = new SSH2Util(serverInfo.getServerIp(), serverInfo.getRemotePort(), serverInfo.getUsername(), serverInfo.getPassword());
            //operateType 01:启动服务 02：停止服务 03：下载日志
            if (operateType.equals("01")) {
                //serverType 服务类型（01:weblogic 02:tomcat 03:jar）
                if (ras.getServiceType().equals("01")) {
                    //osType操作系统类型（01:linux 02:windows）
                    if (serverInfo.getOsType().equals("01")) {
                        command = "nohup sh " + ras.getServicePath() + "/bin/startWebLogic.sh >> " + ras.getServiceLogPath() + " 2>&1 &";
                        ssh.execCommand(command);
                        return OperationResult.buildSuccessResult("启动服务成功");
                    } else if (serverInfo.getOsType().equals("02")) {
                        return OperationResult.buildFailureResult("未开放此功能");
                    } else {
                        return OperationResult.buildFailureResult("操作系统类型有误，请正确填写");
                    }
                } else if (ras.getServiceType().equals("02")) {
                    //osType操作系统类型（01:linux 02:windows）
                    if (serverInfo.getOsType().equals("01")) {
                        return OperationResult.buildFailureResult("未开放此功能");
                    } else if (serverInfo.getOsType().equals("02")) {
                        command = "cmd /c \"" + ras.getServicePath().substring(0, 2) + "&cd " + ras.getServicePath() + "\\bin&startup.bat\"";
                        ssh.execCommand(command);
                        return OperationResult.buildSuccessResult("启动服务成功");
                    } else {
                        return OperationResult.buildFailureResult("操作系统类型有误，请正确填写");
                    }
                } else if(ras.getServiceType().equals("03")){
                    //osType操作系统类型（01:linux 02:windows）
                    if (serverInfo.getOsType().equals("01")) {
                        String  servicePath = ras.getServicePath();
                        if(!servicePath.endsWith(".jar")){
                            return OperationResult.buildFailureResult("服务路径有误，请正确填写");
                        }
                        String[] a = servicePath.split("/");
                        String path = "";
                        for(int i=0;i<a.length-1;i++){
                            path+=a[i]+"/";
                        }
                        command = "cd "+path+";nohup java -jar " + a[a.length-1] +" >> " + ras.getServiceLogPath() + " 2>&1 &";
                        ssh.execCommand(command);
                        return OperationResult.buildSuccessResult("启动服务成功");
                    } else if (serverInfo.getOsType().equals("02")) {
                        return OperationResult.buildFailureResult("未开放此功能");
                    } else {
                        return OperationResult.buildFailureResult("操作系统类型有误，请正确填写");
                    }
                }else {
                    return OperationResult.buildFailureResult("服务类型有误，请正确填写");
                }
            } else if (operateType.equals("02")) {
                //serverType 服务类型（01:weblogic 02:tomcat 03:jar）
                if ("01,02,03".contains(ras.getServiceType())) {
                    //osType操作系统类型（01:linux 02:windows）
                    if (serverInfo.getOsType().equals("01")) {
                        command = "netstat -nlp|grep ':" + ras.getServicePort() + " '" + "|awk '{print $7}'|awk -F'/' 'NR==1{print $1}'|xargs kill -9";
                        ssh.execCommand(command);
                        return OperationResult.buildSuccessResult("停止服务成功");
                    } else if (serverInfo.getOsType().equals("02")) {
                        command = "cmd /c \"for /f \"tokens=5\" %a in ('netstat -ano^|findstr :" + ras.getServicePort() + "') do taskkill /f /pid %a\"";
                        ssh.execCommand(command);
                        return OperationResult.buildSuccessResult("停止服务成功");
                    } else {
                        return OperationResult.buildFailureResult("操作系统类型有误，请正确填写");
                    }
                } else {
                    return OperationResult.buildFailureResult("服务类型有误，请正确填写");
                }
            } else if (operateType.equals("03")) {
                //serverType 服务类型（01:weblogic 02:tomcat 03:jar）
                if ("01,02,03".contains(ras.getServiceType())) {
                    if ("01,02".contains(serverInfo.getOsType())) {
                        String filename = URLEncoder.encode(serverInfo.getServerIp() + "_" + ras.getServicePort() + "_log.txt", "UTF-8");
                        response.addHeader("Content-Disposition", "attachment;filename=" + filename);
                        response.setContentType("multipart/form-data");
                        if(ras.getServiceLogPath()==null||"".equals(ras.getServiceLogPath())){
                            return OperationResult.buildFailureResult("未配置日志路径");
                        }
                        return ssh.downloadFile(response, ras.getServiceLogPath());
                    } else {
                        return OperationResult.buildFailureResult("操作系统类型有误，请正确填写");
                    }
                } else {
                    return OperationResult.buildFailureResult("服务类型有误，请正确填写");
                }
            } else {
                return OperationResult.buildFailureResult("操作失败，请联系管理员");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return OperationResult.buildFailureResult("操作失败，请联系管理员");
        }
    }

    /**
     * 未关联服务器列表数据获取
     * @param rAppServer
     * @return
     */
    public List<RAppServer> findUnRelatedServerList(RAppServer rAppServer) {
        return this.dao.findUnRelatedServerList(rAppServer);
    }

    /**
     * 已关联服务器列表数据获取
     * @param rAppServer
     * @return
     */
    public List<RAppServer> findRelatedServerList(RAppServer rAppServer) {
        return this.dao.findRelatedServerList(rAppServer);
    }

    /**
     * 获取已关联应用列表
     * @param rAppServer
     * @return
     */
    public List<RAppServer> getRelations(RAppServer rAppServer) {
        return this.dao.getRelations(rAppServer);
    }

    /**
     * 移除关联列表
     * @param rAppServer
     */
    public void removeRelations(RAppServer rAppServer) {
        this.dao.removeRelations(rAppServer);
    }

    /**
     * 未关联应用列表数据获取
     * @param rAppServer
     * @return
     */
    public List<RAppServer> findUnRelatedAppList(RAppServer rAppServer) {
        return this.dao.findUnRelatedAppList(rAppServer);
    }

    /**
     * 已关联应用列表数据获取
     * @param rAppServer
     * @return
     */
    public List<RAppServer> findRelatedAppList(RAppServer rAppServer) {
        return this.dao.findRelatedAppList(rAppServer);
    }
}