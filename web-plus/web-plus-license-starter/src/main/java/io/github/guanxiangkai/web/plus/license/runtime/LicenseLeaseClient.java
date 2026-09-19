package io.github.guanxiangkai.web.plus.license.runtime;

import java.io.IOException;

/** 续租传输边界；业务拒绝使用 LicenseException，暂时传输故障使用 IOException。 */
interface LicenseLeaseClient extends AutoCloseable {
    String fetch(String nonce) throws IOException, InterruptedException;
    @Override void close();
}
