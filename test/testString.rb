require 'minirunit'
test_check "Test string:"

#########    test1   #################
testcase='toto'
test_ok(1 == idx = testcase.index('o'))
test_ok(3 == testcase.index('o',idx.succ))
