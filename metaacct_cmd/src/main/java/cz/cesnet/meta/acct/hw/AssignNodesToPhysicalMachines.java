package cz.cesnet.meta.acct.hw;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Martin Kuba makub@ics.muni.cz
 */
public class AssignNodesToPhysicalMachines {

    private final static Logger log = LoggerFactory.getLogger(AssignNodesToPhysicalMachines.class);

    public static void main(String[] args) {
        ApplicationContext springCtx = new ClassPathXmlApplicationContext("spring-context.xml", "perun-config.xml");

        Accounting acct = springCtx.getBean("acct", Accounting.class);
        for(int i=1;i<=16;i++) {
            String node = "elan"+i;
            String machine = "eli-hda1-"+String.format("%03d",i)+"-ostack.priv.cloud.muni.cz";
            System.out.println(node+" => "+machine);
            acct.assignNodes(node,machine);
        }
    }
}
