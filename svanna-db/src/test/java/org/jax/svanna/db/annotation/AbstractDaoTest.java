package org.jax.svanna.db.annotation;

import org.jax.svanna.db.TestDataConfig;
import org.monarchinitiative.svart.GenomicAssemblies;
import org.monarchinitiative.svart.GenomicAssembly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@SpringBootTest(classes = TestDataConfig.class)
public class AbstractDaoTest {

    protected static final GenomicAssembly ASSEMBLY = GenomicAssemblies.GRCh38p13();

    @Autowired
    public DataSource dataSource;
}