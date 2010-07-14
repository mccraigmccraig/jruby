require 'test/minirunit'
test_check "Test File:"

test_equal("file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby",
           File.expand_path(File.join( "file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby/1.8", "..")))

test_equal("file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby",
           File.expand_path("..", "file:/Users/foo/dev/ruby/jruby/lib/jruby-complete.jar!/META-INF/jruby.home/lib/ruby/site_ruby/1.8"))

