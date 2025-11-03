module jnimobile

go 1.19

require (
	github.com/xjasonlyu/tun2socks/v2 v2.5.1
)

// Exclude problematic gvisor version
replace gvisor.dev/gvisor => gvisor.dev/gvisor v0.0.0-20240402165352-486848a4b8df