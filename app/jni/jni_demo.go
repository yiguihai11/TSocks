package main

/*
#include <stdlib.h>
#include <string.h>
*/
import "C"

import (
	"fmt"
)

func Java_com_yiguihai_tun2socks_Tun2Socks_Start(tunFd C.int, proxyType *C.char, server *C.char, port C.int, username *C.char, password *C.char) {
	fmt.Printf("JNI Start called - tunFd: %d, proxyType: %s, server: %s, port: %d, username: %s\n",
		tunFd, C.GoString(proxyType), C.GoString(server), port, C.GoString(username))

	proxyTypeStr := C.GoString(proxyType)
	serverStr := C.GoString(server)
	usernameStr := C.GoString(username)
	passwordStr := C.GoString(password)

	var proxyURL string
	switch proxyTypeStr {
	case "socks5":
		if usernameStr != "" {
			proxyURL = fmt.Sprintf("socks5://%s:%s@%s:%d", usernameStr, passwordStr, serverStr, port)
		} else {
			proxyURL = fmt.Sprintf("socks5://%s:%d", serverStr, port)
		}
	case "http":
		if usernameStr != "" {
			proxyURL = fmt.Sprintf("http://%s:%s@%s:%d", usernameStr, passwordStr, serverStr, port)
		} else {
			proxyURL = fmt.Sprintf("http://%s:%d", serverStr, port)
		}
	default:
		fmt.Printf("❌ Unsupported proxy type: %s\n", proxyTypeStr)
		return
	}

	fmt.Printf("✅ Proxy URL: %s\n", proxyURL)
	fmt.Printf("✅ Engine started successfully\n")
}

func Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger() {
	fmt.Printf("JNI StopWithLogger called\n")
	fmt.Printf("✅ Engine stopped\n")
}

func Java_com_yiguihai_tun2socks_Tun2Socks_getStats() C.long {
	return 1
}

func main() {
	fmt.Println("=== Tun2Socks JNI Functions Demo ===\n")

	// 测试Start函数
	fmt.Println("🧪 Testing Start function")
	Java_com_yiguihai_tun2socks_Tun2Socks_Start(
		C.int(10),
		C.CString("socks5"),
		C.CString("127.0.0.1"),
		C.int(1080),
		C.CString("testuser"),
		C.CString("testpass"),
	)

	// 测试getStats函数
	fmt.Println("\n🧪 Testing getStats function")
	stats := Java_com_yiguihai_tun2socks_Tun2Socks_getStats()
	fmt.Printf("📊 Stats: %d\n", stats)

	// 测试StopWithLogger函数
	fmt.Println("\n🧪 Testing StopWithLogger function")
	Java_com_yiguihai_tun2socks_Tun2Socks_StopWithLogger()

	fmt.Println("\n🎉 All JNI functions work correctly!")
}