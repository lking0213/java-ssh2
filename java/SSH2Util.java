package com.set.trms.util;

import ch.ethz.ssh2.*;
import com.set.direwolf.web.OperationResult;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by qxx on 2018/12/20.
 */
public class SSH2Util {
    private static final com.set.direwolf.log.Logger logger = com.set.direwolf.log.LoggerFactory
            .getLogger(SSH2Util.class);

    private String ip;          //连接linux服务器ip地址
    private int port;           //连接linux服务器端口
    private String username;    //linux服务器用户名
    private String password;    //linux服务器密码

    Connection conn;            //连接

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public SSH2Util(String ip, int port, String username, String password) throws IOException {
        this.ip = ip;
        this.port = port;
        this.username = username;
        this.password = password;
        conn = new Connection(ip, port);
    }

    private void checkAuth() throws IOException {
        boolean isAuth = conn.authenticateWithPassword(username, password);
        if (!isAuth) {
            logger.info("用户名或密码错误，连接{}:{}失败", this.ip, this.port);
            throw new IOException("ssh连接认证失败,请检查输入参数！");
        }
    }

    public static String processStream(InputStream in, String charset) throws IOException {
        byte[] buf = new byte[1024];
        StringBuilder sb = new StringBuilder();
        while (in.read(buf) != -1) {
            sb.append(new String(buf, charset));
        }
        return sb.toString();
    }


    /**
     * 执行命令
     *
     * @return
     * @throws Exception
     */
    public int execCommand(String command) throws Exception {
        Session sess = null;
        try {
            conn.connect((ServerHostKeyVerifier) null, 3000, 0);
            checkAuth();
            logger.info("{}:{}连接成功", this.ip, this.port);
            sess = conn.openSession();
            sess.execCommand(command);
            logger.info("执行命令:{}", command);
            Thread.sleep(500);
            Integer exitStatus = sess.getExitStatus();
            if (exitStatus == null) {
                return -1;
            }
            if (exitStatus == -1) {
                throw new IOException("执行命令失败:" + command);
            }
            return exitStatus;
        } catch (Exception e) {
            logger.info("", e);
            throw e;
        } finally {
            if (sess != null)
                sess.close();
            if (conn != null)
                conn.close();
        }
    }

    /**
     * 上传文件
     *
     * @param localFile
     * @param remoteTargetDirectory
     * @return
     */
    public boolean uploadFile(String localFile, String remoteTargetDirectory){
        conn = new Connection(ip, port);
        SCPClient scp = null;
        try {
            conn.connect((ServerHostKeyVerifier) null, 3000, 0);
            checkAuth();
            logger.info("{}:{}连接成功", this.ip, this.port);
            scp = conn.createSCPClient();
            scp.put(localFile, remoteTargetDirectory, "0644");
            logger.info("上传文件：{}到{}", localFile, remoteTargetDirectory);
            return true;
        } catch (IOException e) {
            logger.info("上传文件失败{}", e);
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null)
                conn.close();
        }
    }

    /**
     * 下载文件
     *
     * @param response
     * @param remoteTargetDirectory
     */
    public OperationResult downloadFile(HttpServletResponse response, String remoteTargetDirectory) {
        if(!isValidPath(remoteTargetDirectory)){
            return OperationResult.buildFailureResult("文件路径有误");
        }
        conn = new Connection(ip, port);
        SCPClient scp = null;
        try {
            conn.connect((ServerHostKeyVerifier) null, 3000, 0);
            checkAuth();
            logger.info("{}:{}连接成功", this.ip, this.port);
            scp = conn.createSCPClient();
            scp.get(remoteTargetDirectory, response.getOutputStream());
            logger.info("下载文件:{}", remoteTargetDirectory);
            return OperationResult.buildSuccessResult("下载文件成功");
        } catch (IOException e) {
            logger.info("下载文件{}失败", remoteTargetDirectory);
            e.printStackTrace();
            return OperationResult.buildFailureResult("下载文件失败");
        } finally {
            if (conn != null)
                conn.close();
        }
    }

    /**
     * 删除文件
     *
     * @param filePath
     * @return
     */
    public OperationResult removeFile(String filePath){
        if(!isValidPath(filePath)){
            return OperationResult.buildFailureResult("文件不存在");
        }
        conn = new Connection(ip, port);
        SFTPv3Client sftp = null;
        try {
            conn.connect((ServerHostKeyVerifier) null, 3000, 0);
            checkAuth();
            logger.info("{}:{}连接成功", this.ip, this.port);
            sftp = new SFTPv3Client(conn);
            sftp.rm(filePath);
            logger.info("删除文件:{}", filePath);
            return OperationResult.buildSuccessResult("删除文件成功");
        } catch (Exception e) {
            logger.info("删除文件{}失败",filePath);
            e.printStackTrace();
            return OperationResult.buildFailureResult("删除文件失败");
        } finally {
            if (sftp != null)
                sftp.close();
            if (conn != null)
                conn.close();
        }
    }

    /**
     * 检查路径是否有效
     * @param path
     * @return
     */
    public boolean isValidPath(String path) {
        conn = new Connection(ip, port);
        SFTPv3Client sftp = null;
        try {
            conn.connect((ServerHostKeyVerifier) null, 3000, 0);
            checkAuth();
            logger.info("{}:{}连接成功", this.ip, this.port);
            sftp = new SFTPv3Client(conn);
            SFTPv3FileAttributes stat = sftp.stat(path);
            boolean a = stat.isDirectory();
            boolean b = stat.isRegularFile();
            return stat.isDirectory() || stat.isRegularFile();
        } catch (Exception e) {
            logger.info("无效路径{}", path);
            e.printStackTrace();
            return false;
        } finally {
            if (sftp != null)
                sftp.close();
            if (conn != null)
                conn.close();
        }
    }
}
