package org.apache.ibatis.localtest;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Test;

import java.io.Reader;

public class LocalTest {


    @Test
    public void test1() throws Exception{

        Reader reader = Resources.getResourceAsReader("mybatis-config.xml");

        SqlSessionFactory build = new SqlSessionFactoryBuilder().build(reader);

        SqlSession sqlSession = build.openSession();

        // 测试用，暂时没有mapper文件
        sqlSession.getMapper(LocalTest.class);
    }

}
