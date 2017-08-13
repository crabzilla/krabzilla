SELECT '*** DATABASES:' as '' ;

SHOW databases ;

use example1

GRANT ALL PRIVILEGES ON example1.* TO 'root'@'localhost' IDENTIFIED BY 'initial123';

CREATE USER 'lula_molusco'@'localhost' IDENTIFIED BY 'initial123';
GRANT ALL PRIVILEGES ON example1.* TO 'lula_molusco'@'localhost' IDENTIFIED BY 'initial123';

FLUSH PRIVILEGES;
