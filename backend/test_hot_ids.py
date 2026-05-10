import asyncio
import httpx

async def test_hot_ids():
    url = "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&hotlist_flag=999"
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        hot_html = response.text
    
    print("Length of HTML:", len(hot_html))
    print("Does it contain 'list0':", 'list0' in hot_html)
    print("Does it contain 'list1':", 'list1' in hot_html)
    print("Does it contain 'baseList':", 'baseList' in hot_html)
    
if __name__ == "__main__":
    asyncio.run(test_hot_ids())
