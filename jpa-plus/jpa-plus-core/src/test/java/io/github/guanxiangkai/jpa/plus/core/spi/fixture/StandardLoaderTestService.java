package io.github.guanxiangkai.jpa.plus.core.spi.fixture;

public class StandardLoaderTestService implements LoaderTestService {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public String name() {
        return "standard";
    }
}
